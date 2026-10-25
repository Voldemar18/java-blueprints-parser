import org.junit.Test;

import static org.junit.Assert.*;

public class DemoTest {

    @Test
    public void emptyArrayHasNoDuplicates() {
        assertFalse(Demo.hasDuplicates(new int[0]));
    }

    @Test
    public void distinctValuesHaveNoDuplicates() {
        assertFalse(Demo.hasDuplicates(new int[]{1, 2, 3}));
    }

    @Test
    public void repeatedValueIsDetected() {
        assertTrue(Demo.hasDuplicates(new int[]{1, 2, 1}));
    }

    @Test
    public void nullPreservesOriginalException() {
        assertThrows(
                NullPointerException.class,
                () -> Demo.hasDuplicates(null)
        );
    }
}