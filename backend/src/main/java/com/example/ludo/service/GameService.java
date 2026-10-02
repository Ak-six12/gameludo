package com.example.ludo.service;

import com.example.ludo.model.GameState;
import jakarta.annotation.PreDestroy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class GameService {

    private static final int MAX_PLAYERS = 4;

    /* Board start positions. These values must match the frontend mapping. */
    private static final int RED_START = 0;
    private static final int GREEN_START = 13;
    private static final int YELLOW_START = 26;
    private static final int BLUE_START = 39;

    /*
     * Player slots are kept fixed:
     * 0 = RED    -> backend start 39
     * 1 = YELLOW -> backend start 13
     * 2 = GREEN  -> backend start 0
     * 3 = BLUE   -> backend start 26
     *
     * Do not change this mapping because the Angular board uses:
     * starts = [39, 13, 0, 26]
     */
    private final int[] playerColors = {
            BLUE_START,
            GREEN_START,
            RED_START,
            YELLOW_START
    };

    /* Safe/star positions on the 52-square board. */
    private final Set<Integer> safe = Set.of(
            0, 8, 13, 21, 26, 34, 39, 47
    );

    private final Map<String, GameState> rooms =
            new ConcurrentHashMap<>();

    private final SecureRandom random =
            new SecureRandom();

    private static final long TURN_TIMEOUT_MS = 20_000L;
    private static final int MAX_TIMEOUTS = 5;

    private final SimpMessagingTemplate messagingTemplate;

    private final ScheduledExecutorService turnScheduler =
            Executors.newSingleThreadScheduledExecutor();

    public GameService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;

        this.turnScheduler.scheduleAtFixedRate(
                this::checkTurnTimeouts,
                1,
                1,
                TimeUnit.SECONDS
        );
    }

    /* =========================================================
       CREATE ROOM
    ========================================================= */

    public synchronized GameState create(String name) {

        String code;

        do {
            code = randomCode();
        } while (rooms.containsKey(code));

        GameState game = new GameState();
        game.roomCode = code;

        addPlayer(
                game,
                UUID.randomUUID().toString(),
                name
        );

        rooms.put(code, game);

        return game;
    }

    /* =========================================================
       JOIN ROOM

       1 player  -> WAITING
       2 players -> PLAYING
       3 players -> allowed
       4 players -> allowed
       5th       -> rejected
    ========================================================= */

    public synchronized GameState join(
            String code,
            String playerId,
            String name
    ) {

        GameState game = get(code);

        String id =
                playerId == null || playerId.isBlank()
                        ? UUID.randomUUID().toString()
                        : playerId;

        /* Same browser joining the same room again. */
        for (GameState.Player player : game.players) {
            if (player.id.equals(id)) {
                return game;
            }
        }

        if (game.players.size() >= MAX_PLAYERS) {
            throw new IllegalArgumentException("Room is full");
        }

        addPlayer(game, id, name);

        /* Start the first turn only when the second player joins. */
        if (game.players.size() >= 2) {
            game.status = "PLAYING";

            if (game.matchStartedAt <= 0L) {
                game.matchStartedAt = System.currentTimeMillis();
                game.matchDurationMs = 0L;
                game.lastDice = null;
                game.consecutiveDiceCount = 0;
            }

            if (game.turnStartedAt <= 0L) {
                startTurn(game);
            }
        }

        broadcast(game);
        return game;
    }

    /* =========================================================
       ADD PLAYER
    ========================================================= */

    private void addPlayer(
            GameState game,
            String id,
            String name
    ) {

        int playerIndex = game.players.size();

        if (playerIndex >= MAX_PLAYERS) {
            throw new IllegalArgumentException("Room is full");
        }

        String playerName =
                name == null || name.isBlank()
                        ? "Player " + (playerIndex + 1)
                        : name.trim();

        game.players.add(
                new GameState.Player(
                        id,
                        playerName,
                        playerIndex
                )
        );
    }

    /* =========================================================
       GET ROOM
    ========================================================= */

    public GameState get(String code) {

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Room code is required");
        }

        GameState game = rooms.get(
                code.trim().toUpperCase()
        );

        if (game == null) {
            throw new IllegalArgumentException("Room not found");
        }

        return game;
    }

    /* =========================================================
       GET PLAYER START POSITION
    ========================================================= */

    private int getStartPosition(GameState.Player player) {
        return playerColors[player.color];
    }

    /* =========================================================
       ROLL DICE
    ========================================================= */

    public synchronized GameState roll(
            String code,
            String playerId
    ) {

        GameState game = get(code);
        checkTurn(game, playerId);

        if (game.dice != null) {
            throw new IllegalStateException("Choose a token first");
        }

        int rolled;

        /*
         * Natural repetition is allowed.
         * A player may get the same number 2 or 3 times in a row,
         * but never 4+ times consecutively.
         */
        if (game.lastDice != null && game.consecutiveDiceCount >= 3) {
            do {
                rolled = 1 + random.nextInt(6);
            } while (rolled == game.lastDice);
        } else {
            rolled = 1 + random.nextInt(6);
        }

        game.dice = rolled;

        if (game.lastDice != null && game.lastDice == rolled) {
            game.consecutiveDiceCount++;
        } else {
            game.lastDice = rolled;
            game.consecutiveDiceCount = 1;
        }

        GameState.Player player =
                game.players.get(game.currentPlayer);

        List<Integer> movableTokens = new ArrayList<>();

        for (int token = 0; token < 4; token++) {
            if (canMove(player, token, game.dice)) {
                movableTokens.add(token);
            }
        }

        /* No legal move. */
        if (movableTokens.isEmpty()) {

            int rolledValue = game.dice;
            game.dice = null;

            /*
             * A normal roll ends the turn.
             * A six keeps the same turn active.
             * IMPORTANT: do not restart the 20-second timer here.
             */
            if (rolledValue != 6) {
                next(game);
            }

            broadcast(game);
            return game;
        }

        /* Exactly one legal token -> move automatically. */
        if (movableTokens.size() == 1) {
            moveInternal(game, movableTokens.get(0));
            broadcast(game);
            return game;
        }

        /* More than one legal token -> frontend chooses. */
        broadcast(game);
        return game;
    }

    /* =========================================================
       MOVE SELECTED TOKEN
    ========================================================= */

    public synchronized GameState move(
            String code,
            String playerId,
            int token
    ) {

        GameState game = get(code);
        checkTurn(game, playerId);

        if (game.dice == null) {
            throw new IllegalStateException("Roll first");
        }

        if (token < 0 || token > 3) {
            throw new IllegalArgumentException("Invalid token");
        }

        if (!canMove(
                game.players.get(game.currentPlayer),
                token,
                game.dice
        )) {
            throw new IllegalStateException("Token cannot move");
        }

        moveInternal(game, token);
        broadcast(game);

        return game;
    }

    /* =========================================================
       CAN MOVE
    ========================================================= */

    private boolean canMove(
            GameState.Player player,
            int token,
            int dice
    ) {

        if (player == null || player.defeated) {
            return false;
        }

        int position = player.tokens[token];

        if (position == 56) {
            return false;
        }

        /* Home -> start requires six. */
        if (position < 0) {
            return dice == 6;
        }

        return position + dice <= 56;
    }

    /* =========================================================
       MOVE INTERNAL
    ========================================================= */

    private void moveInternal(
            GameState game,
            int token
    ) {

        GameState.Player player =
                game.players.get(game.currentPlayer);

        int dice = game.dice;

        if (!canMove(player, token, dice)) {
            throw new IllegalStateException("Token cannot move");
        }

        int oldPosition = player.tokens[token];

        /* A six takes a token from home to its starting square. */
        int newPosition =
                oldPosition < 0
                        ? 0
                        : oldPosition + dice;

        player.tokens[token] = newPosition;

        if (newPosition == 56) {
            player.finished++;
        }

        boolean killed = false;

        if (newPosition >= 0 && newPosition < 51) {
            killed = cut(
                    game,
                    player,
                    newPosition
            );
        }

        /* Dice has been consumed by this move. */
        game.dice = null;

        /* Token-based winner. */
        if (player.finished >= 4) {
            finishGame(game, game.currentPlayer);
            return;
        }

        /*
         * A six OR a kill gives the same player another chance.
         *
         * A new chance means a fresh 20-second timer.
         */
        if (dice != 6 && !killed) {

            next(game);

        } else {

            /*
             * Same player gets another chance.
             * Restart the 20-second timer.
             */
            startTurn(game);
        }
    }

    /* =========================================================
       KILL / CUT
    ========================================================= */

    private boolean cut(
            GameState game,
            GameState.Player me,
            int relativeStep
    ) {

        int boardIndex =
                (getStartPosition(me) + relativeStep) % 52;

        if (safe.contains(boardIndex)) {
            return false;
        }

        boolean killed = false;

        for (GameState.Player opponent : game.players) {

            if (opponent == me || opponent.defeated) {
                continue;
            }

            for (int token = 0; token < 4; token++) {

                int opponentStep = opponent.tokens[token];

                if (opponentStep >= 0 && opponentStep < 52) {

                    int opponentBoardIndex =
                            (getStartPosition(opponent) + opponentStep) % 52;

                    if (opponentBoardIndex == boardIndex) {
                        opponent.tokens[token] = -1;
                        me.kills++;
                        killed = true;
                    }
                }
            }
        }

        return killed;
    }

    /* =========================================================
       TURN VALIDATION
    ========================================================= */

    private void checkTurn(
            GameState game,
            String playerId
    ) {

        if (!"PLAYING".equals(game.status)) {
            throw new IllegalStateException("Game is not active");
        }

        if (game.players.isEmpty()) {
            throw new IllegalStateException("No players in room");
        }

        if (game.currentPlayer < 0 ||
                game.currentPlayer >= game.players.size()) {
            throw new IllegalStateException("Invalid current player");
        }

        GameState.Player current =
                game.players.get(game.currentPlayer);

        if (current.defeated) {
            throw new IllegalStateException("Current player is defeated");
        }

        if (playerId == null || !current.id.equals(playerId)) {
            throw new IllegalStateException("Not your turn");
        }
    }

    /* =========================================================
       NEXT ACTIVE PLAYER
    ========================================================= */

    private void next(GameState game) {

        if (game.players.isEmpty()) {
            return;
        }

        int totalPlayers = game.players.size();

        for (int i = 0; i < totalPlayers; i++) {

            game.currentPlayer =
                    (game.currentPlayer + 1) % totalPlayers;

            GameState.Player nextPlayer =
                    game.players.get(game.currentPlayer);

            if (!nextPlayer.defeated) {
                /* New player's turn starts now. */
                startTurn(game);
                return;
            }
        }

        finishIfOnlyOnePlayerRemains(game);
    }

    /* =========================================================
       CHECK IF ONE ACTIVE PLAYER REMAINS
    ========================================================= */

    private void finishIfOnlyOnePlayerRemains(GameState game) {

        int activePlayers = 0;
        int lastActivePlayer = -1;

        for (int i = 0; i < game.players.size(); i++) {

            GameState.Player player = game.players.get(i);

            if (!player.defeated) {
                activePlayers++;
                lastActivePlayer = i;
            }
        }

        if (activePlayers == 1) {
            finishGame(game, lastActivePlayer);
        }
    }

    private void finishGame(GameState game, int winnerIndex) {
        game.winner = winnerIndex;
        game.status = "FINISHED";
        game.dice = null;
        game.turnStartedAt = 0L;

        if (game.matchStartedAt > 0L) {
            game.matchDurationMs = Math.max(0L,
                    System.currentTimeMillis() - game.matchStartedAt);
        }
    }

    /* =========================================================
       TURN TIMER
    ========================================================= */

    private void startTurn(GameState game) {
        game.turnStartedAt = System.currentTimeMillis();
        game.dice = null;
    }

    private void checkTurnTimeouts() {

        synchronized (this) {

            long now = System.currentTimeMillis();
            List<GameState> changed = new ArrayList<>();

            for (GameState game : rooms.values()) {

                if (!"PLAYING".equals(game.status)) {
                    continue;
                }

                if (game.players.isEmpty() ||
                        game.turnStartedAt <= 0L) {
                    continue;
                }

                if (now - game.turnStartedAt < TURN_TIMEOUT_MS) {
                    continue;
                }

                if (game.currentPlayer < 0 ||
                        game.currentPlayer >= game.players.size()) {
                    game.currentPlayer = 0;
                }

                GameState.Player player =
                        game.players.get(game.currentPlayer);

                if (player.defeated) {
                    next(game);
                    changed.add(game);
                    continue;
                }

                /* Record the missed turn. */
                player.turnTimeouts = Math.min(
                        MAX_TIMEOUTS,
                        player.turnTimeouts + 1
                );

                game.dice = null;

                /* Five missed turns => defeated. */
                if (player.turnTimeouts >= MAX_TIMEOUTS) {

                    player.defeated = true;

                    /* Remove all of the defeated player's coins. */
                    player.tokens = new int[]{-1, -1, -1, -1};
                    player.finished = 0;

                    /* If only one active player remains, that player wins. */
                    finishIfOnlyOnePlayerRemains(game);

                    if (!"FINISHED".equals(game.status)) {
                        next(game);
                    }

                } else {
                    /* Skip this turn and continue with the next active player. */
                    next(game);
                }

                changed.add(game);
            }

            for (GameState game : changed) {
                broadcast(game);
            }
        }
    }

    /* =========================================================
       WEBSOCKET BROADCAST
    ========================================================= */

    private void broadcast(GameState game) {
        messagingTemplate.convertAndSend(
                "/topic/room/" + game.roomCode,
                game
        );
    }

    @PreDestroy
    public void shutdownTimer() {
        turnScheduler.shutdownNow();
    }

    /* =========================================================
       RANDOM ROOM CODE
    ========================================================= */

    private String randomCode() {

        String chars =
                "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            builder.append(
                    chars.charAt(
                            random.nextInt(chars.length())
                    )
            );
        }

        return builder.toString();
    }
}
