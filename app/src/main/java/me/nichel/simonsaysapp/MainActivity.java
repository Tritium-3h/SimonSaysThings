package me.nichel.simonsaysapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Stack;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import timber.log.Timber;

public class MainActivity extends Activity {
    //RX
    private final CompositeDisposable disposable = new CompositeDisposable();
    private final Subject<String> input = PublishSubject.create();

    //FIREBASE
    private final FirebaseDatabase database = FirebaseDatabase.getInstance();
    private static final String STATUS_DB = "status";
    private static final String LED_DB = "led";
    private final DatabaseReference STATUS_DB_REF = database.getReference(STATUS_DB);
    private final DatabaseReference LED_DB_REF = database.getReference(LED_DB);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.v("onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_green).setOnClickListener(v -> input.onNext("green"));
        findViewById(R.id.btn_red).setOnClickListener(v -> input.onNext("red"));

        STATUS_DB_REF.setValue("none");
        STATUS_DB_REF.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(final DataSnapshot snapshot) {
                Timber.v("onDataChange");
                Timber.d("snapshot: %s", snapshot);

                final String status = snapshot.getValue(String.class);
                Timber.d("status: " + status);

                ((TextView) findViewById(R.id.tv_status)).setText(status);
            }

            @Override
            public void onCancelled(final DatabaseError error) {
                Timber.v("onCancelled");
                Timber.d("error: %s", error);
            }
        });

        LED_DB_REF.child("color").setValue("none");
        LED_DB_REF.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(final DataSnapshot snapshot) {
                Timber.v("onDataChange");
                Timber.d("snapshot: %s", snapshot);

                final String color = snapshot.child("color").getValue(String.class);
                Timber.d("color: " + color);

                //FIXME with android things
                switch (color) {
                    case "red": {
                        findViewById(R.id.lbl_red).setEnabled(true);
                        findViewById(R.id.lbl_green).setEnabled(false);
                        break;
                    }

                    case "green": {
                        findViewById(R.id.lbl_red).setEnabled(false);
                        findViewById(R.id.lbl_green).setEnabled(true);
                        break;
                    }

                    default: {
                        findViewById(R.id.lbl_red).setEnabled(false);
                        findViewById(R.id.lbl_green).setEnabled(false);
                    }
                }
            }

            @Override
            public void onCancelled(final DatabaseError error) {
                Timber.v("onCancelled");
                Timber.d("error: %s", error);
            }
        });

        startGame(10);
    }

    @Override
    protected void onDestroy() {
        Timber.v("onDestroy");

        super.onDestroy();
        disposable.clear();
    }

    private void startGame(final int level) {
        Timber.v("startGame");
        Timber.d("level: %d", level);

        final Stack<String> simon = new Stack<>();
        for (int i = 0; i < level; ++i) {
            simon.add("green");
        }

        disposable.add(
                Observable.fromArray(simon.toArray())
                        .doOnSubscribe(onSubscriber -> {
                            Timber.wtf("start level communication");
                            STATUS_DB_REF.setValue("read");
                        })
                        .map(obj -> (String) obj)
                        .flatMap(value -> Observable.just(value, "none"))
                        .zipWith(Observable.interval(1, TimeUnit.SECONDS), (value, timer) -> value)
                        .subscribe(
                                value -> LED_DB_REF.child("color").setValue(value),
                                Timber::e,
                                () -> {
                                    Timber.wtf("level communication completed");

                                    disposable.add(
                                            input.timeout(10, TimeUnit.MINUTES) //FIXME
                                                    .doOnSubscribe(onSubscriber -> {
                                                        Timber.wtf("start game");

                                                        LED_DB_REF.child("color").setValue("none");
                                                        STATUS_DB_REF.setValue("start");
                                                    })
                                                    .doOnNext(value -> LED_DB_REF.child("color").setValue(value))
                                                    .flatMap(value -> {
                                                        if (value.equals(simon.peek())) {
                                                            return Observable.just(simon.pop());
                                                        } else {
                                                            return Observable.error(new Exception("wrong input"));
                                                        }
                                                    })
                                                    .take(level)
                                                    .subscribe(
                                                            value -> {
                                                                Timber.d("value: %s", value);
                                                                STATUS_DB_REF.setValue("listening");
                                                            },
                                                            err -> {
                                                                Timber.wtf("game over");

                                                                LED_DB_REF.child("color").setValue("none");
                                                                STATUS_DB_REF.setValue("lose");

                                                                Observable.timer(3, TimeUnit.SECONDS).subscribe(o -> this.startGame(1));
                                                            },
                                                            () -> {
                                                                Timber.wtf("complete level: %d", level);

                                                                LED_DB_REF.child("color").setValue("none");
                                                                STATUS_DB_REF.setValue("win");

                                                                Observable.timer(3, TimeUnit.SECONDS).subscribe(o -> this.startGame(level + 1));
                                                            })
                                    );
                                }
                        )
        );
    }
}
