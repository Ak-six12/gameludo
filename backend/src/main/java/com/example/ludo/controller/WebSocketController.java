package com.example.ludo.controller;

import com.example.ludo.model.GameState;
import com.example.ludo.service.GameService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {
    private final GameService service;
    public WebSocketController(GameService service){this.service=service;}

    public record Action(String roomCode,String playerId,Integer token){}

    @MessageMapping("/game/roll")
    @SendTo("/topic/game-events")
    public GameState roll(Action a){return service.roll(a.roomCode(),a.playerId());}

    @MessageMapping("/game/move")
    @SendTo("/topic/game-events")
    public GameState move(Action a){return service.move(a.roomCode(),a.playerId(),a.token());}
}
