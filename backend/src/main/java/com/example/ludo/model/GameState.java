package com.example.ludo.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {

    public String roomCode;

    public List<Player> players = new ArrayList<>();

    public int currentPlayer = 0;

    public Integer dice;

    public Integer winner;

    /* Server timestamp when the match actually started. */
    public long matchStartedAt = 0L;

    /* Final duration in milliseconds. Set when the match finishes. */
    public long matchDurationMs = 0L;

    /* Last server-generated dice value. Used by the UI even when an
       automatic move consumes game.dice immediately. */
    public Integer lastDice;

    /* Number of consecutive identical dice results. */
    public int consecutiveDiceCount = 0;

    public String status = "WAITING";

    /*
     * Server timestamp when the current turn started.
     * The frontend uses this value for the 20-second countdown.
     */
    public long turnStartedAt = 0L;

    public static class Player {

        public String id;

        public String name;

        /*
         * Player slot:
         * 0 = RED
         * 1 = YELLOW
         * 2 = GREEN
         * 3 = BLUE
         */
        public int color;

        /*
         * -1 = home
         * 0..50 = common track
         * 51..55 = five finish-lane cells
         * 56 = center peak / completed
         */
        public int[] tokens = {-1, -1, -1, -1};

        public int finished = 0;

        /* Number of turns allowed to expire. Maximum is 5. */
        public int turnTimeouts = 0;

        /* Number of opponent coins this player has sent back home. */
        public int kills = 0;

        /* True after this player misses 5 turns. */
        public boolean defeated = false;

        public Player() {
        }

        public Player(
                String id,
                String name,
                int color
        ) {
            this.id = id;
            this.name = name;
            this.color = color;
        }
    }
}
