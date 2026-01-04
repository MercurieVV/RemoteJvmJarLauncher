package io.github.mercurievv.rjjl.it;

import io.github.mercurievv.rjjl.Main;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    private final Main main = new Main();

    @Test
    void run() {
        try {
            Main.main(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//        assertEquals(2, calculator.add(1, 1));
    }
}