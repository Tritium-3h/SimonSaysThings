package me.nichel.simonsaysapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import timber.log.Timber;

public class MainActivity extends Activity {
    //COLOR
    private static final Map<String, String> COLOR_MAP = new HashMap<>();

    static {
        COLOR_MAP.put("red", "0");
        COLOR_MAP.put("green", "1");
        COLOR_MAP.put("blue", "2");
        COLOR_MAP.put("yellow", "3");
    }

    //RX
    private final CompositeDisposable disposable = new CompositeDisposable();
    private final Subject<String> input = PublishSubject.create();

    //FIREBASE
    private final FirebaseDatabase database = FirebaseDatabase.getInstance();
    private static final String STATUS_DB = "status";
    private static final String LED_DB = "led";
    private final DatabaseReference STATUS_DB_REF = database.getReference(STATUS_DB);
    private final DatabaseReference LED_DB_REF = database.getReference(LED_DB);
    private int counter = 0;

    //THINGS
    private final PeripheralManagerService svc = new PeripheralManagerService();
    private final Map<String, Gpio> leds = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.v("onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            //BUTTONS
            final Button btnRed = new Button("IO2", Button.LogicState.PRESSED_WHEN_LOW);
            btnRed.setOnButtonEventListener((button, pressed) -> {
                Timber.d("button[%s]: %s", button, pressed);

                if (pressed) {
                    input.onNext(COLOR_MAP.get("red"));
                }
            });

//            final Button btnGreeb = new Button("BTN_GREEN_PIN", Button.LogicState.PRESSED_WHEN_LOW);
//            btnGreeb.setOnButtonEventListener((button, pressed) -> {
//                input.onNext(COLOR_MAP.get("green"));
//            });
//
//            final Button btnBlue = new Button("BTN_BLUE_PIN", Button.LogicState.PRESSED_WHEN_LOW);
//            btnBlue.setOnButtonEventListener((button, pressed) -> {
//                input.onNext(COLOR_MAP.get("blue"));
//            });
//
//            final Button btnYellow = new Button("BTN_YELLOW_PIN", Button.LogicState.PRESSED_WHEN_LOW);
//            btnYellow.setOnButtonEventListener((button, pressed) -> {
//                input.onNext(COLOR_MAP.get("yellow"));
//            });

            //LEDS
            final Gpio redLed = svc.openGpio("IO4");
            redLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            leds.put(COLOR_MAP.get("red"), redLed);

//            final Gpio greenLed = svc.openGpio("IO4");
//            greenLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
//            leds.put(COLOR_MAP.get("green"), greenLed);
//
//            final Gpio blueLed = svc.openGpio("LED_BLUE_PIN");
//            blueLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
//            leds.put(COLOR_MAP.get("blue"), greenLed);
//
//            final Gpio yellowLed = svc.openGpio("LED_YELLOW_PIN");
//            yellowLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
//            leds.put(COLOR_MAP.get("yellow"), greenLed);
        } catch (IOException e) {
            Timber.w(e);
        }

        STATUS_DB_REF.setValue("none");
        STATUS_DB_REF.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(final DataSnapshot snapshot) {
                Timber.v("onDataChange");
                Timber.d("snapshot: %s", snapshot);
            }

            @Override
            public void onCancelled(final DatabaseError error) {
                Timber.v("onCancelled");
                Timber.d("error: %s", error);
            }
        });


        LED_DB_REF.setValue(new SimonEvent("none", 0));
        LED_DB_REF.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(final DataSnapshot snapshot) {
                Timber.v("onDataChange");
                Timber.d("snapshot: %s", snapshot);

                if (snapshot.child("counter") != null) {
                    counter = Integer.getInteger(snapshot.child("counter").getValue(String.class), 0);
                    Timber.d("counter: " + counter);
                }

                if (snapshot.child("color") != null) {
                    final String color = snapshot.child("color").getValue(String.class);
                    Timber.d("color: " + color);

                    try {
                        for (final String key : leds.keySet()) {
                            leds.get(key).setValue(key.equals(color));
                        }
                    } catch (IOException e) {
                        Timber.w(e);
                    }
                }
            }

            @Override
            public void onCancelled(final DatabaseError error) {
                Timber.v("onCancelled");
                Timber.d("error: %s", error);
            }
        });

        startGame(generateRandomSimon(10), 1);
    }

    @Override
    protected void onDestroy() {
        Timber.v("onDestroy");

        super.onDestroy();
        disposable.clear();
    }

    private Stack<String> generateRandomSimon(final int lenght) {
        final int size = COLOR_MAP.size();
        final String[] keys = COLOR_MAP.keySet().toArray(new String[size]);
        final Random random = new Random();

        final Stack<String> simon = new Stack<>();
        for (int i = 0; i < lenght; ++i) {
            simon.add(COLOR_MAP.get(keys[random.nextInt(size)]));
        }

        return simon;
    }

    private void startGame(final Stack<String> simon, final int level) {
        Timber.v("startGame");

        final List<String> currentSimon = simon.subList(0, level);
        Timber.d("simon: %s", simon);
        Timber.d("subs simon: %s", currentSimon);

        Observable.range(0, level)
                .doOnSubscribe(onSubscriber -> {
                    //FIXME update status?
                })
                .zipWith(Observable.interval(1, TimeUnit.SECONDS), (value, timer) -> value)
                .subscribe(
                        i -> LED_DB_REF.setValue(new SimonEvent(simon.get(i), counter + i)),
                        Timber::e,
                        () -> {
                            //FIXME update status?

                            input.timeout(10, TimeUnit.DAYS)
                                    .doOnNext(value -> LED_DB_REF.child("color").setValue(value))
                                    .flatMap(value -> {
                                        if (value.equals(currentSimon.get(0))) {
                                            return Observable.just(simon.remove(0));
                                        } else {
                                            return Observable.error(new Exception("wrong input"));
                                        }
                                    })
                                    .take(level)
                                    .subscribe(
                                            value -> Timber.d("input: %s", value),
                                            err -> {
                                                Timber.d("game over");

                                                STATUS_DB_REF.setValue("lose");

                                                Observable.timer(3, TimeUnit.SECONDS).subscribe(o -> {
                                                    final Stack<String> newSimon = generateRandomSimon(10);
                                                    this.startGame(newSimon, 1);
                                                });
                                            },
                                            () -> {
                                                Timber.d("level %d completed", level);

                                                STATUS_DB_REF.setValue("win");

                                                Observable.timer(3, TimeUnit.SECONDS).subscribe(o -> this.startGame(simon, 2));
                                            });
                        }
                );

    }
}
