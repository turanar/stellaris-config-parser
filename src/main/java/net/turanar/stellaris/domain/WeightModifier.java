package net.turanar.stellaris.domain;

import net.turanar.stellaris.parser.StellarisParser.*;

import java.util.ArrayList;
import java.util.List;

public class WeightModifier extends Modifier {
    public Float factor = 1.0f;
    public Integer add  = 0;

    @Override
    public String toString() {
        String format = "(×%s)";
        if(this.add > 0) format = "(+%s)";
        if(type != null) format += " %s";
        return String.format(format, String.valueOf(factor), type != null ? type.parse(pair) : "");
    }
}
