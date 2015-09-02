package de.gbv.ole;

import static org.junit.Assert.*;
import org.junit.Test;

public class CallNumber_UT {
    @Test
    public void shelvingOrder() {
        String [] t = {
                "2014 A 1",      "02014 A     00001",
                "2014A1"  ,      "02014 A     00001",
                "POL 592 : F64", "POL   00592 F     00064",
                "POL592:F64",    "POL   00592 F     00064",
        };
        for (int i=0; i<t.length; i+=2) {
            assertEquals(t[i], t[i+1], CallNumber.getShelvingOrder(t[i]));
        }
    }
}
