package de.gbv.ole;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Marc21ToOleBulk_UT {
    @Test
    public void empty2space() {
        assertEquals(Marc21ToOleBulk.empty2space(null), " ");
        assertEquals(Marc21ToOleBulk.empty2space(""),   " ");
        assertEquals(Marc21ToOleBulk.empty2space(" ") , " ");
        assertEquals(Marc21ToOleBulk.empty2space(" a ") , " a ");
    }
}
