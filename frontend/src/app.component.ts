import { Component } from '@angular/core';



import { CommonModule } from '@angular/common';



import { FormsModule } from '@angular/forms';



import { HttpClient, HttpClientModule } from '@angular/common/http';



import { firstValueFrom } from 'rxjs';



import * as Stomp from 'stompjs';







interface TrackCell {



  row: number;



  col: number;



  index: number;



}







interface TokenRef {



  player: any;



  playerIndex: number;



  tokenIndex: number;



}







@Component({



  selector: 'app-root',



  standalone: true,



  imports: [CommonModule, FormsModule, HttpClientModule],



  templateUrl: './app.component.html',



  styleUrls: ['./app.component.css']



})



export class AppComponent {







  name = 'Player';



  room = '';
  copiedRoom = false;



  myId = crypto.randomUUID();



  game: any = null;



  client: any = null;



  error = '';







  diceRolling = false;



  displayDice: number | null = null;



  movingToken = false;
  private moveRequestInProgress = false;



  animatedPositions: Record<string, number> = {};



  private diceTimer: any = null;



  private suppressSocketUpdate = false;



  private readonly diceAnimationMs = 1100;

  private turnClockTimer: any = null;

  private nowMs = Date.now();

  private readonly turnDurationMs = 20_000;

  private readonly maxTimeouts = 5;

  centerFlipKeys: Record<string, boolean> = {};
  private centerFlipTimers: Record<string, any> = {};







  /*



   * Player slot / board color:



   * 0 RED    -> bottom-left  -> start 39



   * 1 YELLOW -> top-right    -> start 13



   * 2 GREEN  -> top-left     -> start 0



   * 3 BLUE   -> bottom-right  -> start 26



   *



   * This gives opposite positions to the first two players.



   */



  readonly colors = [



    '#ef2b2b',



    '#ffd600',



    '#12b956',



    '#159fe8'



  ];







  readonly colorNames = [



    'red',



    'yellow',



    'green',



    'blue'



  ];







readonly starts = [39, 13, 0, 26];







  /* Standard 52-square outer path of a 15 x 15 Ludo board. */



  readonly track: TrackCell[] = [



    { row: 6, col: 1, index: 0 },



    { row: 6, col: 2, index: 1 },



    { row: 6, col: 3, index: 2 },



    { row: 6, col: 4, index: 3 },



    { row: 6, col: 5, index: 4 },



    { row: 5, col: 6, index: 5 },



    { row: 4, col: 6, index: 6 },



    { row: 3, col: 6, index: 7 },



    { row: 2, col: 6, index: 8 },



    { row: 1, col: 6, index: 9 },



    { row: 0, col: 6, index: 10 },



    { row: 0, col: 7, index: 11 },



    { row: 0, col: 8, index: 12 },



    { row: 1, col: 8, index: 13 },



    { row: 2, col: 8, index: 14 },



    { row: 3, col: 8, index: 15 },



    { row: 4, col: 8, index: 16 },



    { row: 5, col: 8, index: 17 },



    { row: 6, col: 9, index: 18 },



    { row: 6, col: 10, index: 19 },



    { row: 6, col: 11, index: 20 },



    { row: 6, col: 12, index: 21 },



    { row: 6, col: 13, index: 22 },



    { row: 6, col: 14, index: 23 },



    { row: 7, col: 14, index: 24 },



    { row: 8, col: 14, index: 25 },



    { row: 8, col: 13, index: 26 },



    { row: 8, col: 12, index: 27 },



    { row: 8, col: 11, index: 28 },



    { row: 8, col: 10, index: 29 },



    { row: 8, col: 9, index: 30 },



    { row: 9, col: 8, index: 31 },



    { row: 10, col: 8, index: 32 },



    { row: 11, col: 8, index: 33 },



    { row: 12, col: 8, index: 34 },



    { row: 13, col: 8, index: 35 },



    { row: 14, col: 8, index: 36 },



    { row: 14, col: 7, index: 37 },



    { row: 14, col: 6, index: 38 },



    { row: 13, col: 6, index: 39 },



    { row: 12, col: 6, index: 40 },



    { row: 11, col: 6, index: 41 },



    { row: 10, col: 6, index: 42 },



    { row: 9, col: 6, index: 43 },



    { row: 8, col: 5, index: 44 },



    { row: 8, col: 4, index: 45 },



    { row: 8, col: 3, index: 46 },



    { row: 8, col: 2, index: 47 },



    { row: 8, col: 1, index: 48 },



    { row: 8, col: 0, index: 49 },



    { row: 7, col: 0, index: 50 },



    { row: 6, col: 0, index: 51 }



  ];







  /*
   * Relative path used by the game:
   * 0..50  = common track.
   * 51..55 = five finish-lane cells.
   * 56     = center peak / completed.
   *
   * The player's own starting square is intentionally NOT repeated after
   * a full round. So position 50 goes directly into the finish lane.
   * Conceptually these finish cells are steps 53..57 and the peak is 58.
   */



  readonly finishLanes = [



    [



      { row: 7, col: 1 },



      { row: 7, col: 2 },



      { row: 7, col: 3 },



      { row: 7, col: 4 }



    ],



    [



      { row: 7, col: 13 },



      { row: 7, col: 12 },



      { row: 7, col: 11 },



      { row: 7, col: 10 }



    ],



    [



      { row: 1, col: 7 },



      { row: 2, col: 7 },



      { row: 3, col: 7 },



      { row: 4, col: 7 }



    ],



    [



      { row: 13, col: 7 },



      { row: 12, col: 7 },



      { row: 11, col: 7 },



      { row: 10, col: 7 }



    ]



  ];







  constructor(private http: HttpClient) {}







  async create(): Promise<void> {



    try {



      this.error = '';



      const game: any = await firstValueFrom(



        this.http.post('http://localhost:8080/api/game/rooms', {



          name: this.name



        })



      );



      this.game = game;



      this.room = game.roomCode;



      this.myId = game.players[0].id;



      this.connect();



    } catch (e: any) {



      this.error = e?.error?.message || 'Create failed';



    }



  }







  async join(): Promise<void> {



    try {



      this.error = '';



      const code = this.room.trim().toUpperCase();



      if (!code) {



        this.error = 'Enter a room code';



        return;



      }







      const game: any = await firstValueFrom(



        this.http.post(



          `http://localhost:8080/api/game/rooms/${code}/join`,



          {



            playerId: this.myId,



            name: this.name



          }



        )



      );







      this.game = game;



      this.room = game.roomCode;



      this.connect();



    } catch (e: any) {



      this.error = e?.error?.message || 'Join failed';



    }



  }







  connect(): void {



    if (this.client?.connected) return;







    this.client = Stomp.over(



      new WebSocket('ws://localhost:8080/ws')



    );







    this.client.debug = () => {};







    this.client.connect(



      {},



      () => {


this.client.subscribe(
  `/topic/room/${this.game.roomCode}`,
  async (message: any) => {

  if (this.suppressSocketUpdate) {
    return;
  }

  try {

    const before = this.game
      ? JSON.parse(JSON.stringify(this.game))
      : null;

    const result = JSON.parse(message.body);

    if (!before) {

      this.game = result;
      this.startTurnClock();
      this.error = '';

      return;
    }

    /*
     * First show the dice animation.
     */
    const isRemoteRoll =
      before.dice == null &&
      Number.isInteger(result.lastDice) &&
      result.lastDice >= 1 &&
      result.lastDice <= 6;

    if (isRemoteRoll) {

      await this.animateRemoteDice(
        Number(result.lastDice)
      );
    }

    /*
     * THEN animate the token.
     */
    await this.animateRemoteGameUpdate(
      before,
      result
    );

    this.error = '';

  } catch {

    this.error =
      'Invalid game update received';

  }

}
);

      },



      () => {



        this.error = 'WebSocket connection failed';



      }



    );



  }







  async roll(): Promise<void> {

    if (

      !this.game ||

      !this.isMine() ||

      this.game.dice != null ||

      this.diceRolling ||

      this.movingToken

    ) {

      return;

    }



    const before = JSON.parse(JSON.stringify(this.game));

    const playerIndex = before.currentPlayer;



    try {

      this.error = '';

      this.diceRolling = true;

      this.suppressSocketUpdate = true;

      this.displayDice = null;



      if (this.diceTimer) clearInterval(this.diceTimer);



      this.diceTimer = setInterval(() => {

        this.displayDice = Math.floor(Math.random() * 6) + 1;

      }, 75);



      const result: any = await firstValueFrom(

        this.http.post(

          `http\://localhost:8080/api/game/rooms/${this.room}/roll?playerId=${this.myId}`,

          {}

        )

      );



      if (this.diceTimer) {

        clearInterval(this.diceTimer);

        this.diceTimer = null;

      }



      const rolled = this.resolveRolledDice(before, result, playerIndex);

      this.displayDice = rolled;



      const beforePlayer = before.players?.[playerIndex];

      const afterPlayer = result.players?.[playerIndex];

      const visualGame = JSON.parse(JSON.stringify(result));



      if (beforePlayer && afterPlayer) {

        const changedToken = beforePlayer.tokens.findIndex(

          (value: number, i: number) =>

            value !== afterPlayer.tokens?.[i]

        );



        if (changedToken >= 0) {

          visualGame.players[playerIndex].tokens[changedToken] =

            beforePlayer.tokens[changedToken];

        }

      }



      this.prepareKilledTokensForVisualState(

        before,

        visualGame,

        playerIndex

      );



      this.game = visualGame;



      await this.sleep(this.diceAnimationMs);

      this.diceRolling = false;



      if (beforePlayer && afterPlayer) {

        const changedToken = beforePlayer.tokens.findIndex(

          (value: number, i: number) =>

            value !== afterPlayer.tokens?.[i]

        );



        if (changedToken >= 0) {

          const from = beforePlayer.tokens[changedToken];

          const to = afterPlayer.tokens[changedToken];



          this.movingToken = true;

          this.animatedPositions[`${playerIndex}-${changedToken}`] = from;



          await this.animateTokenMovement(

            playerIndex,

            changedToken,

            from,

            to

          );



          this.clearAnimatedPosition(

            playerIndex,

            changedToken

          );



          await this.animateKilledTokens(

            before,

            result,

            playerIndex,

            changedToken

          );



          this.detectFinishedTokens(before, result);
          this.game = result;

          this.movingToken = false;

        } else {

          this.game = result;

        }

      } else {

        this.game = result;

      }






    } catch (e: any) {

      if (this.diceTimer) {

        clearInterval(this.diceTimer);

        this.diceTimer = null;

      }



      this.diceRolling = false;

      this.movingToken = false;

      this.displayDice = null;

      this.error = e?.error?.message || 'Roll failed';

    } finally {

      this.suppressSocketUpdate = false;



      if (this.diceTimer) {

        clearInterval(this.diceTimer);

        this.diceTimer = null;

      }

    }

  }



  async move(
  tokenIndex: number,
  playerIndex?: number
): Promise<void> {

  if (
    !this.game ||
    !this.isMine() ||
    this.movingToken
  ) {
    return;
  }

  const currentPlayerIndex =
    this.game.currentPlayer;

  /*
   * Always use the current player.
   * The playerIndex passed from the clicked
   * token is only used to make sure the
   * clicked coin belongs to the current player.
   */
  if (
    playerIndex !== undefined &&
    playerIndex !== currentPlayerIndex
  ) {
    return;
  }

  /*
   * Validate only after receiving the click.
   * Do not disable the HTML button.
   */
  if (!this.canMove(tokenIndex, currentPlayerIndex)) {
    return;
  }

  const before =
    JSON.parse(JSON.stringify(this.game));

  const oldPosition =
    before.players?.[currentPlayerIndex]
      ?.tokens?.[tokenIndex];

  try {

    this.error = '';

    this.movingToken = true;
    this.suppressSocketUpdate = true;

    const result: any =
      await firstValueFrom(
        this.http.post(
          `http://localhost:8080/api/game/rooms/${this.room}/move/${tokenIndex}?playerId=${this.myId}`,
          {}
        )
      );

    const newPosition =
      result?.players?.[currentPlayerIndex]
        ?.tokens?.[tokenIndex];

    /*
     * Keep the token visually at its old
     * position while the movement animation
     * runs.
     */
    this.prepareKilledTokensForAnimation(
      before,
      result
    );

    const visualGame =
      JSON.parse(JSON.stringify(result));

    if (
      visualGame?.players?.[currentPlayerIndex] &&
      typeof oldPosition === 'number'
    ) {
      visualGame.players[
        currentPlayerIndex
      ].tokens[tokenIndex] = oldPosition;
    }

    this.game = visualGame;

    if (
      typeof oldPosition === 'number' &&
      typeof newPosition === 'number'
    ) {

      const key =
        `${currentPlayerIndex}-${tokenIndex}`;

      this.animatedPositions[key] =
        oldPosition;

      await this.animateTokenMovement(
        currentPlayerIndex,
        tokenIndex,
        oldPosition,
        newPosition
      );

      this.clearAnimatedPosition(
        currentPlayerIndex,
        tokenIndex
      );

      await this.animateKilledTokens(
        before,
        result,
        currentPlayerIndex,
        tokenIndex
      );
    }

    this.detectFinishedTokens(
  before,
  result
);

/*
 * Finally use the real backend state.
 */
this.game = result;

/*
 * Backend may have started a fresh turn
 * after a six or a kill.
 *
 * Restart the local timer using the
 * new turnStartedAt received from backend.
 */
this.startTurnClock();
  } catch (e: any) {

    this.error =
      e?.error?.message || 'Move failed';

  } finally {
  this.movingToken = false;
  this.moveRequestInProgress = false;
  this.suppressSocketUpdate = false;
}
}



  private prepareKilledTokensForVisualState(

    before: any,

    visualGame: any,

    moverPlayerIndex: number

  ): void {

    if (!before?.players || !visualGame?.players) return;



    for (let pi = 0; pi < before.players.length; pi++) {

      if (pi === moverPlayerIndex) continue;



      const beforePlayer = before.players[pi];

      const afterPlayer = visualGame.players[pi];



      if (!beforePlayer || !afterPlayer) continue;



      for (let ti = 0; ti < 4; ti++) {

        const from = beforePlayer.tokens?.[ti];

        const to = afterPlayer.tokens?.[ti];



        if (

          typeof from === 'number' &&

          from >= 0 &&

          from < 52 &&

          to === -1

        ) {

          afterPlayer.tokens[ti] = from;

        }

      }

    }

  }



  private prepareKilledTokensForAnimation(

    before: any,

    result: any

  ): void {

    if (!before?.players || !result?.players) return;



    for (let pi = 0; pi < before.players.length; pi++) {

      const beforePlayer = before.players[pi];

      const afterPlayer = result.players[pi];



      if (!beforePlayer || !afterPlayer) continue;



      for (let ti = 0; ti < 4; ti++) {

        const from = beforePlayer.tokens?.[ti];

        const to = afterPlayer.tokens?.[ti];



        if (

          typeof from === 'number' &&

          from >= 0 &&

          from < 52 &&

          to === -1

        ) {

          this.animatedPositions[`${pi}-${ti}`] = from;

        }

      }

    }

  }



  getTurnSeconds(): number {

    if (!this.game || this.game.status !== 'PLAYING') return 0;



    const started = Number(this.game.turnStartedAt || 0);

    if (!started) return 20;



    const remaining =

      this.turnDurationMs - (this.nowMs - started);



    return Math.max(0, Math.ceil(remaining / 1000));

  }



  getTimeoutCount(playerIndex: number): number {

    const value = Number(

      this.game?.players?.[playerIndex]?.turnTimeouts || 0

    );



    return Math.min(this.maxTimeouts, Math.max(0, value));

  }



  isCurrentPlayer(playerIndex: number): boolean {

    return !!(

      this.game &&

      this.game.status === 'PLAYING' &&

      this.game.currentPlayer === playerIndex

    );

  }



  getTurnTimerClass(): string {

    const seconds = this.getTurnSeconds();



    if (seconds <= 5) return 'danger';

    if (seconds <= 10) return 'warning';

    return 'normal';

  }



  getTurnProgress(): number {

    return Math.max(

      0,

      Math.min(

        100,

        (this.getTurnSeconds() / 20) * 100

      )

    );

  }



  private startTurnClock(): void {

    if (this.turnClockTimer) {

      clearInterval(this.turnClockTimer);

    }



    this.nowMs = Date.now();



    this.turnClockTimer = setInterval(() => {

      this.nowMs = Date.now();

    }, 250);

  }



  isMine(): boolean {
    const currentPlayer =
      this.game?.players?.[this.game.currentPlayer];

    return !!(
      this.game?.status === 'PLAYING' &&
      currentPlayer &&
      currentPlayer.id === this.myId &&
      !currentPlayer.defeated
    );
  }

  canMove(tokenIndex: number, playerIndex?: number): boolean {
    if (!this.game || this.game.dice == null || !this.isMine()) {
      return false;
    }

    const currentPlayer = this.game.currentPlayer;
    const targetPlayer = playerIndex ?? currentPlayer;

    if (targetPlayer !== currentPlayer) return false;

    const player = this.game.players?.[targetPlayer];
    if (!player || player.defeated) return false;

    const position = player.tokens?.[tokenIndex];
    const dice = this.game.dice;

    if (position === 56) return false;
    if (position < 0) return dice === 6;

    return position + dice <= 56;
  }

  centerFinishedTokens(): TokenRef[] {
    const result: TokenRef[] = [];
    if (!this.game?.players) return result;

    this.game.players.forEach((player: any, playerIndex: number) => {
      (player.tokens || []).forEach((position: number, tokenIndex: number) => {
        if (position === 56) {
          result.push({ player, playerIndex, tokenIndex });
        }
      });
    });

    return result;
  }

  centerCoinClass(playerIndex: number, tokenIndex: number): string {
    return this.centerFlipKeys[`${playerIndex}-${tokenIndex}`] ? 'flip-once' : '';
  }

  trackCenterToken(index: number, token: TokenRef): string {
    return `${token.playerIndex}-${token.tokenIndex}`;
  }

  private detectFinishedTokens(before: any, after: any): void {
    if (!before?.players || !after?.players) return;

    for (let pi = 0; pi < after.players.length; pi++) {
      const beforePlayer = before.players?.[pi];
      const afterPlayer = after.players?.[pi];
      if (!beforePlayer || !afterPlayer) continue;

      for (let ti = 0; ti < 4; ti++) {
        const from = beforePlayer.tokens?.[ti];
        const to = afterPlayer.tokens?.[ti];
        if (from !== 56 && to === 56) {
          this.playCenterFlip(pi, ti);
        }
      }
    }
  }

  private playCenterFlip(playerIndex: number, tokenIndex: number): void {
    const key = `${playerIndex}-${tokenIndex}`;

    if (this.centerFlipTimers[key]) {
      clearTimeout(this.centerFlipTimers[key]);
    }

    this.centerFlipKeys[key] = false;

    setTimeout(() => {
      this.centerFlipKeys[key] = true;

      this.centerFlipTimers[key] = setTimeout(() => {
        this.centerFlipKeys[key] = false;
        delete this.centerFlipTimers[key];
      }, 850);
    }, 0);
  }

  getMatchDurationText(): string {
    if (!this.game) return '00:00';

    const duration = Number(this.game.matchDurationMs || 0);
    const started = Number(this.game.matchStartedAt || 0);
    const elapsed = duration > 0
      ? duration
      : (started > 0 ? Math.max(0, Date.now() - started) : 0);

    const totalSeconds = Math.floor(elapsed / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (hours > 0) {
      return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
    }

    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  getWinnerName(): string {
    const winnerIndex = Number(this.game?.winner);
    return this.game?.players?.[winnerIndex]?.name || 'Winner';
  }

  getMostKills(): number {
    return (this.game?.players || []).reduce(
      (max: number, player: any) => Math.max(max, Number(player.kills || 0)),
      0
    );
  }

  getMostKillerNames(): string {
    const players = this.game?.players || [];
    const maxKills = this.getMostKills();
    if (!players.length || maxKills <= 0) return 'No coins were killed';

    return players
      .filter((player: any) => Number(player.kills || 0) === maxKills)
      .map((player: any) => player.name)
      .join(', ');
  }

  getMostKillerLabel(): string {
    const maxKills = this.getMostKills();
    if (maxKills <= 0) return 'No coin kills';
    return `${this.getMostKillerNames()} — ${maxKills} coin${maxKills === 1 ? '' : 's'} killed`;
  }

  getPlayerColor(playerIndex: number): string {



    return this.colors[playerIndex] || '#777';



  }







  getPlayerColorName(playerIndex: number): string {



    return this.colorNames[playerIndex] || 'red';



  }







  getHomeClass(playerIndex: number): string {



    return [



      'red-home',



      'yellow-home',



      'green-home',



      'blue-home'



    ][playerIndex] || 'red-home';



  }







  getFinishLaneIndex(playerIndex: number): number {



    return [3, 2, 0, 1][playerIndex] ?? 0;



  }







  getFinishedClass(playerIndex: number): string {



    return `${this.getPlayerColorName(playerIndex)}-finished`;



  }







  getControlPlayerIndex(): number {



    const index = this.game?.players?.findIndex(



      (p: any) => p.id === this.myId



    );



    return index>= 0 ? index : 0;



  }







  getOpponentControlIndex(): number {



    const me = this.getControlPlayerIndex();



    const players = this.game?.players || [];







    if (players.length <= 1) return me === 0 ? 1 : 0;







    const opponent = players.findIndex(



      (p: any, index: number) => index !== me



    );







    return opponent>= 0 ? opponent : me;



  }







  homeTokenVisible(playerIndex: number, tokenIndex: number): boolean {
    const key = `${playerIndex}-${tokenIndex}`;

    /* Keep a killed token out of home while it animates backwards. */
    if (this.animatedPositions[key] !== undefined) {
      return false;
    }

    const player = this.game?.players?.[playerIndex];

    return !!(
      player &&
      !player.defeated &&
      player.tokens[tokenIndex] < 0
    );
  }

  trackTokens(trackIndex: number): TokenRef[] {



    const result: TokenRef[] = [];







    if (!this.game?.players) return result;







    this.game.players.forEach(



      (player: any, playerIndex: number) => {



        player.tokens.forEach(



          (backendPosition: number, tokenIndex: number) => {



            const key = `${playerIndex}-${tokenIndex}`;



            const animated = this.animatedPositions[key];



            const position = animated !== undefined



              ? animated



              : backendPosition;







            if (position < 0 || position > 50) return;

            const actualTrackPosition =
              (this.starts[playerIndex] + position) % 52;







            if (actualTrackPosition === trackIndex) {



              result.push({



                player,



                playerIndex,



                tokenIndex



              });



            }



          }



        );



      }



    );







    return result;



  }







  finishTokens(playerIndex: number, laneIndex: number): TokenRef[] {



    const result: TokenRef[] = [];



    const player = this.game?.players?.[playerIndex];







    if (!player) return result;







    player.tokens.forEach(



      (backendPosition: number, tokenIndex: number) => {



        const key = `${playerIndex}-${tokenIndex}`;



        const animated = this.animatedPositions[key];



        const position = animated !== undefined



          ? animated



          : backendPosition;







        if (position === 51 + laneIndex) {



          result.push({



            player,



            playerIndex,



            tokenIndex



          });



        }



      }



    );







    return result;



  }







  finishedTokens(playerIndex: number): number {



    const player = this.game?.players?.[playerIndex];



    if (!player) return 0;







    return player.tokens.filter(



      (position: number, tokenIndex: number) => {



        const key = `${playerIndex}-${tokenIndex}`;



        const animated = this.animatedPositions[key];



        const visual = animated !== undefined ? animated : position;



        return visual === 56;



      }



    ).length;



  }







  tokenClass(playerIndex: number, tokenIndex: number): string {



    const classes = [



      'token',



      this.getPlayerColorName(playerIndex)



    ];







    if (



      this.game?.currentPlayer === playerIndex &&



      this.canMove(tokenIndex, playerIndex)



    ) {



      classes.push('movable');



    }







    return classes.join(' ');



  }







  getStackTransform(count: number, index: number): string {



    if (count <= 1) {



      return 'translate(-50%, -50%)';



    }







    const positions = [



      'translate(calc(-50% - 5px), calc(-50% - 5px))',



      'translate(calc(-50% + 5px), calc(-50% + 5px))',



      'translate(calc(-50% + 5px), calc(-50% - 5px))',



      'translate(calc(-50% - 5px), calc(-50% + 5px))'



    ];







    return positions[index] || 'translate(-50%, -50%)';



  }







  isStartPosition(index: number): boolean {



    return [0, 13, 26, 39].includes(index);



  }







  isSafePosition(index: number): boolean {



    return [8, 21, 34, 47].includes(index);



  }







  startArrow(index: number): string {



    switch (index) {



      case 0: return '→';



      case 13: return '↓';



      case 26: return '←';



      case 39: return '↑';



      default: return '';



    }



  }







  playerStatus(): string {
    if (!this.game) return '';

    const me = this.game.players?.find(
      (p: any) => p.id === this.myId
    );

    if (me?.defeated) {
      return '☠️ You are defeated';
    }

    if (this.game.status === 'WAITING') {
      return '⏳ Waiting for another player';
    }

    if (this.game.status === 'FINISHED') {
      return `🏆 ${this.game.players?.[this.game.winner]?.name || ''} wins!`;
    }

    if (this.isMine()) return '🎯 Your turn';

    return `⏳ ${this.game.players?.[this.game.currentPlayer]?.name || ''}'s turn`;
  }

  async copyRoomCode(): Promise<void> {
    const code = this.game?.roomCode || this.room;
    if (!code) return;

    try {
      await navigator.clipboard.writeText(code);
      this.copiedRoom = true;
    } catch {
      const textArea = document.createElement('textarea');
      textArea.value = code;
      textArea.style.position = 'fixed';
      textArea.style.opacity = '0';
      document.body.appendChild(textArea);
      textArea.select();

      try {
        document.execCommand('copy');
        this.copiedRoom = true;
      } finally {
        document.body.removeChild(textArea);
      }
    }

    setTimeout(() => {
      this.copiedRoom = false;
    }, 1500);
  }

  returnToCreateRoom(): void {
    this.error = '';
    this.diceRolling = false;
    this.movingToken = false;
    this.displayDice = null;
    this.animatedPositions = {};
    this.centerFlipKeys = {};

    if (this.diceTimer) {
      clearInterval(this.diceTimer);
      this.diceTimer = null;
    }

    if (this.turnClockTimer) {
      clearInterval(this.turnClockTimer);
      this.turnClockTimer = null;
    }

    if (this.client?.connected) {
      this.client.disconnect();
    }

    this.client = null;
    this.game = null;
    this.room = '';
    this.copiedRoom = false;
    this.myId = crypto.randomUUID();
  }

  diceFace(value: number | null): string {



    switch (value) {



      case 1: return '⚀';



      case 2: return '⚁';



      case 3: return '⚂';



      case 4: return '⚃';



      case 5: return '⚄';



      case 6: return '⚅';



      default: return '🎲';



    }



  }







  getStartPosition(playerIndex: number): number {



  return this.starts[playerIndex] ?? 0;



}







  private resolveRolledDice(before: any, result: any, playerIndex: number): number {



    if (typeof result?.lastDice === 'number') {
      return result.lastDice;
    }

    if (typeof result?.dice === 'number') {



      return result.dice;



    }







    const beforePlayer = before?.players?.[playerIndex];



    const afterPlayer = result?.players?.[playerIndex];







    if (!beforePlayer || !afterPlayer) {



      return this.displayDice ?? 1;



    }







    const changedToken = beforePlayer.tokens.findIndex(



      (value: number, i: number) => value !== afterPlayer.tokens?.[i]



    );







    if (changedToken < 0) {



      return this.displayDice ?? 1;



    }







    const from = beforePlayer.tokens[changedToken];



    const to = afterPlayer.tokens[changedToken];







    // Home -> start can only happen with a six.



    if (from < 0 && to === 0) return 6;







    return Math.abs(to - from);



  }



private async animateRemoteGameUpdate(
  before: any,
  result: any
): Promise<void> {

  if (!before?.players || !result?.players) {
    this.game = result;
    this.startTurnClock();
    return;
  }

  const changedTokens: Array<{
    playerIndex: number;
    tokenIndex: number;
    from: number;
    to: number;
  }> = [];

  /*
   * Find every token whose backend position changed.
   */
  for (
    let pi = 0;
    pi < result.players.length;
    pi++
  ) {

    const beforePlayer = before.players?.[pi];
    const afterPlayer = result.players?.[pi];

    if (!beforePlayer || !afterPlayer) {
      continue;
    }

    for (let ti = 0; ti < 4; ti++) {

      const from = beforePlayer.tokens?.[ti];
      const to = afterPlayer.tokens?.[ti];

      if (
        typeof from === 'number' &&
        typeof to === 'number' &&
        from !== to
      ) {

        changedTokens.push({
          playerIndex: pi,
          tokenIndex: ti,
          from,
          to
        });

      }

    }
  }

  /*
   * If there is no token movement, just update
   * the game normally.
   *
   * Example:
   * - player joins
   * - dice rolled but multiple tokens are available
   * - timer update
   */
  if (changedTokens.length === 0) {

    this.game = result;
    this.startTurnClock();

    return;
  }

  /*
   * Keep all changed tokens at their OLD positions.
   * This prevents the remote screen from jumping
   * directly to the destination.
   */
  const visualGame = JSON.parse(
    JSON.stringify(result)
  );

  for (const change of changedTokens) {

    if (
      visualGame.players?.[change.playerIndex]
    ) {

      visualGame.players[
        change.playerIndex
      ].tokens[
        change.tokenIndex
      ] = change.from;

    }
  }

  this.game = visualGame;

  /*
   * Keep the timer synchronized with backend.
   */
  this.startTurnClock();

  /*
   * IMPORTANT:
   *
   * Wait for the same dice animation duration
   * used by the player who rolled.
   *
   * This prevents:
   *
   * LEFT  -> token already moved
   * RIGHT -> dice still rolling
   */
  await this.sleep(this.diceAnimationMs);

  /*
   * Animate normal token movement.
   */
  for (const change of changedTokens) {

    /*
     * Killed tokens are handled separately below.
     */
    if (
      change.from >= 0 &&
      change.from < 52 &&
      change.to === -1
    ) {
      continue;
    }

    /*
     * Home -> starting square.
     */
    if (
      change.from < 0 &&
      change.to === 0
    ) {

      this.animatedPositions[
        `${change.playerIndex}-${change.tokenIndex}`
      ] = change.from;

      await this.animateTokenMovement(
        change.playerIndex,
        change.tokenIndex,
        change.from,
        change.to
      );

      this.clearAnimatedPosition(
        change.playerIndex,
        change.tokenIndex
      );

      continue;
    }

    /*
     * Normal board / finish movement.
     */
    if (
      change.from >= 0 &&
      change.to >= 0
    ) {

      this.animatedPositions[
        `${change.playerIndex}-${change.tokenIndex}`
      ] = change.from;

      await this.animateTokenMovement(
        change.playerIndex,
        change.tokenIndex,
        change.from,
        change.to
      );

      this.clearAnimatedPosition(
        change.playerIndex,
        change.tokenIndex
      );
    }
  }

  /*
   * Animate killed coins backwards to HOME.
   */
  await this.animateKilledTokens(
    before,
    result,
    result.currentPlayer,
    -1
  );

  /*
   * Trigger centre/finish animation.
   */
  this.detectFinishedTokens(
    before,
    result
  );

  /*
   * Finally apply the real backend state.
   */
  this.game = result;

  /*
   * Restart/update the timer using the new
   * turnStartedAt value.
   */
  this.startTurnClock();
}



  private async animateKilledTokens(



    before: any,



    result: any,



    moverPlayerIndex: number,



    moverTokenIndex: number



  ): Promise<void> {



    if (!before?.players || !result?.players) return;







    for (let pi = 0; pi < before.players.length; pi++) {



      if (pi === moverPlayerIndex) continue;







      const beforePlayer = before.players[pi];



      const afterPlayer = result.players[pi];







      if (!beforePlayer || !afterPlayer) continue;







      for (let ti = 0; ti < 4; ti++) {



        const from = beforePlayer.tokens?.[ti];



        const to = afterPlayer.tokens?.[ti];







        // A killed token changes from a board position to home (-1).



        if (



          typeof from === 'number' &&



          from>= 0 &&



          from < 52 &&



          to === -1



        ) {



          const key = `${pi}-${ti}`;







          // Keep it visible at the killed square even though backend already



          // moved it to home. Then walk it backwards to relative position 0.



          this.animatedPositions[key] = from;







          for (let position = from - 1; position>= 0; position--) {



            await this.sleep(105);



            this.animatedPositions[key] = position;



          }







          await this.sleep(180);



          this.clearAnimatedPosition(pi, ti);



        }



      }



    }



  }







  private clearAnimatedPosition(



    playerIndex: number,



    tokenIndex: number



  ): void {



    delete this.animatedPositions[`${playerIndex}-${tokenIndex}`];



  }







  private sleep(ms: number): Promise<void> {



    return new Promise(resolve => setTimeout(resolve, ms));



  }


private async animateRemoteDice(
  finalDice: number
): Promise<void> {

  if (
    !Number.isInteger(finalDice) ||
    finalDice < 1 ||
    finalDice > 6
  ) {
    return;
  }

  /*
   * Start the same dice animation used
   * by the player who rolled.
   */
  this.diceRolling = true;

  if (this.diceTimer) {
    clearInterval(this.diceTimer);
  }

  this.diceTimer = setInterval(() => {

    this.displayDice =
      Math.floor(Math.random() * 6) + 1;

  }, 75);

  /*
   * Give both browsers approximately the
   * same animation duration.
   */
  await this.sleep(this.diceAnimationMs);

  if (this.diceTimer) {

    clearInterval(this.diceTimer);
    this.diceTimer = null;

  }

  /*
   * IMPORTANT:
   *
   * Never randomly choose the final value.
   * Use the value received from backend.
   */
  this.displayDice = finalDice;

  this.diceRolling = false;
}




  private async animateTokenMovement(
    playerIndex: number,
    tokenIndex: number,
    from: number,
    to: number
  ): Promise<void> {
    const key = `${playerIndex}-${tokenIndex}`;

    /* Home -> starting square. A six puts it directly on start. */
    if (from < 0) {
      this.animatedPositions[key] = 0;
      await this.sleep(350);
      return;
    }

    if (to === from) {
      this.animatedPositions[key] = to;
      await this.sleep(120);
      return;
    }

    /* Normally positions only increase. This also safely handles a
       visual wrap from 51 -> 0 if an older server state is received. */
    if (to > from) {
      for (let position = from + 1; position <= to; position++) {
        this.animatedPositions[key] = position;
        await this.sleep(150);
      }
      return;
    }

    this.animatedPositions[key] = from;
    for (let position = from + 1; position <= 51; position++) {
      this.animatedPositions[key] = position;
      await this.sleep(150);
    }
    for (let position = 0; position <= to; position++) {
      this.animatedPositions[key] = position;
      await this.sleep(150);
    }
  }



  ngOnDestroy(): void {

    if (this.diceTimer) {

      clearInterval(this.diceTimer);

      this.diceTimer = null;

    }



    if (this.turnClockTimer) {

      clearInterval(this.turnClockTimer);

      this.turnClockTimer = null;

    }



    if (this.client?.connected) {

      this.client.disconnect();

    }

  }



}