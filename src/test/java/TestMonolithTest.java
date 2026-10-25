import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class TestMonolithTest {

    @Test(timeout = 2000)
    public void emptyInputReturnsEmptyListAndPrintsNothing() throws Exception {
        Captured result = capture(Collections.emptyList());

        assertEquals(Collections.emptyList(), result.values);
        assertEquals("", result.stdout);
    }

    @Test(timeout = 2000)
    public void preservesOrderDuplicatesAndOriginalInput() throws Exception {
        List<String> input = new ArrayList<>(
                Arrays.asList("b", "", "a", "b")
        );
        List<String> original = new ArrayList<>(input);

        Captured result = capture(input);

        assertEquals(Arrays.asList("b", "a", "b"), result.values);
        assertEquals(original, input);
        assertNotSame(input, result.values);

        assertEquals(matches(6), result.stdout);
    }

    @Test(timeout = 2000)
    public void duplicatePairIsProcessedInBothDirections() throws Exception {
        Captured result = capture(Arrays.asList("x", "x"));

        assertEquals(Arrays.asList("x", "x"), result.values);
        assertEquals(matches(4), result.stdout);
    }

    @Test(timeout = 2000)
    public void nullListThrowsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> new TestMonolith().processData(null)
        );
    }

    @Test(timeout = 2000)
    public void nullElementPreservesExceptionAndEarlierOutput() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;

        try (PrintStream replacement = new PrintStream(
                output, true, StandardCharsets.UTF_8
        )) {
            System.setOut(replacement);

            assertThrows(
                    NullPointerException.class,
                    () -> new TestMonolith().processData(
                            Arrays.asList("x", null)
                    )
            );
        } finally {
            System.setOut(previous);
        }

        assertEquals(
                matches(1),
                output.toString(StandardCharsets.UTF_8)
        );
    }

    @Test(timeout = 2000)
    public void calculatePreservesBoundaryCases() {
        TestMonolith monolith = new TestMonolith();

        assertEquals(3, monolith.calculate(7, 3));
        assertEquals(3, monolith.calculate(3, 7));
        assertEquals(4, monolith.calculate(4, 4));
        assertEquals(-5, monolith.calculate(-2, -5));
        assertEquals(-5, monolith.calculate(-5, -2));
    }

    private Captured capture(List<String> input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        List<String> values;

        try (PrintStream replacement = new PrintStream(
                output, true, StandardCharsets.UTF_8
        )) {
            System.setOut(replacement);
            values = new TestMonolith().processData(input);
        } finally {
            System.setOut(previous);
        }

        return new Captured(
                values,
                output.toString(StandardCharsets.UTF_8)
        );
    }

    private String matches(int count) {
        return ("Match" + System.lineSeparator()).repeat(count);
    }

    private static class Captured {
        final List<String> values;
        final String stdout;

        Captured(List<String> values, String stdout) {
            this.values = values;
            this.stdout = stdout;
        }
    }
}