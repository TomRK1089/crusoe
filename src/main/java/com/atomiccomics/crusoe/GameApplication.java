package com.atomiccomics.crusoe;

import com.atomiccomics.crusoe.event.Event;
import com.atomiccomics.crusoe.item.Item;
import com.atomiccomics.crusoe.player.Player;
import com.atomiccomics.crusoe.world.Grapher;
import com.atomiccomics.crusoe.world.World;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GameApplication extends Application {

    private static final System.Logger LOG = System.getLogger(GameApplication.class.getName());

    public static void main(final String... args) {
        LOG.log(System.Logger.Level.INFO, "Starting JavaFX application");
        Application.launch(args);
    }

    private final CompositeDisposable disposable = new CompositeDisposable();

    @Override
    public void start(final Stage stage) throws Exception {
        LOG.log(System.Logger.Level.DEBUG, "Displaying initial stage");

        final var canvas = new Canvas();
        canvas.setWidth(800);
        canvas.setHeight(600);

        final var scene = new Scene(new Pane(canvas));
        stage.setScene(scene);
        stage.show();

        final var game = new Game();

        final var random = new Random();

        final var mover = new Mover();
        final var builder = new Builder();
        final var audioPlayer = new AudioPlayer();
        final var grapher = new Grapher();
        final var drawer = new Drawer();
        final var holder = new Holder();
        final var picker = new Picker(game::updatePlayer, game::updateWorld);

        game.register(mover::process);
        game.register(builder::process);
        game.register(audioPlayer::process);
        game.register(grapher::process);
        game.register(picker::process);
        game.register(drawer::process);
        game.register(holder::process);

        final var WIDTH = 32;
        final var HEIGHT = 32;

        game.updateWorld(w -> w.resize(new World.Dimensions(WIDTH, HEIGHT)));

        // Set up some random walls
        final var wallCount = random.nextInt(10) + 10;
        final var walls = IntStream.range(0, wallCount)
                .mapToObj(i -> new World.Coordinates(random.nextInt(WIDTH), random.nextInt(HEIGHT)))
                .collect(Collectors.toSet());
        walls.forEach(c -> game.updateWorld(w -> w.buildWallAt(c)));

        World.Coordinates candidateStartingLocation;
        do {
            candidateStartingLocation = new World.Coordinates(random.nextInt(WIDTH), random.nextInt(HEIGHT));
        } while(walls.contains(candidateStartingLocation));
        final var playerStartsAt = candidateStartingLocation;
        game.updateWorld(w -> w.spawnPlayerAt(playerStartsAt));

        World.Coordinates candidateItemLocation;
        do {
            candidateItemLocation = new World.Coordinates(random.nextInt(WIDTH), random.nextInt(HEIGHT));
        } while(walls.contains(candidateItemLocation) || playerStartsAt.equals(candidateItemLocation));
        final var pickaxeStartsAt = candidateItemLocation;
        game.updateWorld(w -> w.spawnItemAt(Item.PICKAXE, pickaxeStartsAt));

        final var renderer = new Renderer(canvas);

        disposable.add(Observable.interval(17, TimeUnit.MILLISECONDS)
            .subscribe(i -> Platform.runLater(renderer.render(drawer.snapshot()))));

        final var keysToDirections = Map.of(
                KeyCode.W, World.Direction.NORTH,
                KeyCode.A, World.Direction.WEST,
                KeyCode.S, World.Direction.SOUTH,
                KeyCode.D, World.Direction.EAST
        );

        final var keysPressed = Observable.<KeyEvent>create(emitter -> {
            final EventHandler<KeyEvent> listener = emitter::onNext;
            emitter.setCancellable(() -> scene.removeEventHandler(KeyEvent.KEY_PRESSED, listener));
            scene.addEventHandler(KeyEvent.KEY_PRESSED, listener);
        }).share();

        final Observable<Function<World, List<Event<?>>>> updateFromPlayerMovement = keysPressed
                .map(KeyEvent::getCode)
                .filter(keysToDirections::containsKey)
                .map(keysToDirections::get)
                .throttleFirst(100, TimeUnit.MILLISECONDS)
                .flatMap(direction -> {
                   if(mover.isFacing(direction) && mover.isLegalMove(direction)) {
                        return Observable.just(w -> w.move(direction));
                   } else {
                       return Observable.just(w -> w.turn(direction));
                   }
                });

        final Observable<Function<World, List<Event<?>>>> updateFromPlayerAction = keysPressed
                .map(KeyEvent::getCode)
                .filter(c -> c == KeyCode.E)
                .throttleFirst(100, TimeUnit.MILLISECONDS)
                .flatMap(x -> {
                    if(builder.canBuildWherePlayerLooking()) {
                        return Observable.just(w -> w.buildWallAt(builder.playerTarget()));
                    } else if(builder.canDestroyWherePlayerLooking()) {
                        return Observable.just(w -> w.destroyWallAt(builder.playerTarget()));
                    }
                    return Observable.empty();
                });

        final Observable<Function<Player, List<Event<?>>>> updateFromPlayerDrop = keysPressed
                .map(KeyEvent::getCode)
                .filter(c -> c == KeyCode.Q)
                .throttleFirst(100, TimeUnit.MILLISECONDS)
                .flatMap(x -> {
                    if(holder.hasItems()) {
                        //TODO Decide which item to drop
                        return Observable.just(p -> p.dropItem(Item.PICKAXE));
                    }
                    return Observable.empty();
                });

        disposable.add(Observable.merge(updateFromPlayerMovement, updateFromPlayerAction)
                .subscribe(game::updateWorld));
        disposable.add(updateFromPlayerDrop.subscribe(game::updatePlayer));

        disposable.add(keysPressed.map(KeyEvent::getCode).filter(c -> c == KeyCode.ESCAPE).subscribe(k -> Platform.exit()));
    }

    @Override
    public void stop() throws Exception {
        disposable.dispose();
    }
}
