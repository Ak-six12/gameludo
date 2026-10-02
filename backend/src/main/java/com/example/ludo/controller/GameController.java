package com.example.ludo.controller;

import com.example.ludo.model.GameState;
import com.example.ludo.service.GameService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins="*")
public class GameController {
    private final GameService service;
    private final SimpMessagingTemplate ws;

    public GameController(GameService service,SimpMessagingTemplate ws){this.service=service;this.ws=ws;}

    public record CreateRequest(String name){}
    public record JoinRequest(String playerId,String name){}

    @PostMapping("/rooms")
    public GameState create(@RequestBody CreateRequest r){
        GameState g=service.create(r.name());
        g.players.get(0).id=g.players.get(0).id;
        return g;
    }

    @PostMapping("/rooms/{code}/join")
    public GameState join(@PathVariable String code,@RequestBody JoinRequest r){
        GameState g=service.join(code,r.playerId(),r.name());
        publish(g);
        return g;
    }

    @GetMapping("/rooms/{code}")
    public GameState state(@PathVariable String code){return service.get(code);}

    @PostMapping("/rooms/{code}/roll")
    public GameState roll(@PathVariable String code,@RequestParam String playerId){
        GameState g=service.roll(code,playerId); publish(g); return g;
    }

    @PostMapping("/rooms/{code}/move/{token}")
    public GameState move(@PathVariable String code,@PathVariable int token,@RequestParam String playerId){
        GameState g=service.move(code,playerId,token); publish(g); return g;
    }

    private void publish(GameState g){ws.convertAndSend("/topic/room/"+g.roomCode,g);}
}
