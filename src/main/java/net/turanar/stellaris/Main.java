package net.turanar.stellaris;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.turanar.stellaris.domain.*;
import net.turanar.stellaris.parser.StellarisParser;
import net.turanar.stellaris.parser.StellarisParser.*;
import net.turanar.stellaris.visitor.ModifierTypeAdapter;
import net.turanar.stellaris.visitor.TechnologyVisitor;
import net.turanar.stellaris.visitor.UnlockVisitor;
import net.turanar.stellaris.visitor.WeightModifierTypeAdapter;
import org.apache.commons.lang3.StringUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import static net.turanar.stellaris.Global.*;

public class Main {
    private static Map<String,Technology> technologies = new HashMap<>();

    public static void visitUnlock(GameObject type) throws IOException {
        Files.list(Paths.get(type.folder))
        .filter(path->path.toString().endsWith(type.filter))
        .forEach((path) -> {
            System.out.println(path);
            try {
                StellarisParser parser = parser(path);
                if(parser == null) return;

                UnlockVisitor visitor = new UnlockVisitor(type, technologies);
                visitor.visitFile(parser.file());

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) throws Exception {
        Global.init();
        SortedSet<Technology> array = new TreeSet<Technology>();
        TechnologyVisitor visitor = new TechnologyVisitor();

        Files.list(Paths.get("common/technology"))
        .filter(path->path.toString().endsWith(".txt"))
        .forEach((path) -> {
            try {
                System.out.println(path);
                StellarisParser parser = parser(path);
                if(parser == null) return;

                List<PairContext> pairs = parser.file().pair();
                for(PairContext pair : pairs) {
                    Technology tech = visitor.visitPair(pair);
                    technologies.put(tech.key, tech);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        techLoop: for(Technology tech : technologies.values()) {
            if(tech.tier == null) tech.tier = 0;
            if(tech.cost == null) tech.cost = 0;
            if(tech.is_start_tech) tech.prerequisites.clear();

            Iterator<String> iter = tech.prerequisites.iterator();
            while(iter.hasNext()) {
                String preq = iter.next();
                Technology reqTech = technologies.get(preq);
                if(reqTech.is_start_tech && reqTech.area != tech.area) iter.remove();
            }

            for(String preq : tech.prerequisites) {
                Technology reqTech = technologies.get(preq);
                HashMap<String,String> item = new HashMap<>();
                item.put("key", reqTech.key);
                item.put("name", reqTech.name);
                tech.prerequisites_names.add(item);

            }
            tech.base_weight = tech.base_weight*tech.base_factor;

            // Re-order prerequisite so the most costly is first AND must be the same AREA
            tech.prerequisites.sort(new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    Technology parent1 = technologies.get(o1);
                    Technology parent2 = technologies.get(o2);

                    // Same AREA - will compare Costs
                    if(parent1.area.equals(tech.area) && parent2.area.equals(tech.area)) {
                        return parent1.cost.compareTo(parent2.cost);
                    }
                    // Not same AREA - Will prioritize the one the same as child tech
                    if(parent1.area.equals(tech.area) && !parent2.area.equals(tech.area)) {
                        return -1;
                    }
                    if(!parent1.area.equals(tech.area) && parent2.area.equals(tech.area)) {
                        return 1;
                    }

                    return 0;
                }
            });
            array.add(tech);
        }

        for(GameObject object : GameObject.values()) {
            visitUnlock(object);
        }

        GsonBuilder builder = new GsonBuilder();

        builder.registerTypeAdapter(WeightModifier.class, new WeightModifierTypeAdapter());
        builder.registerTypeAdapter(Modifier.class, new ModifierTypeAdapter());
        builder.setPrettyPrinting();
        Gson gson = builder.create();

        FileOutputStream fos = new FileOutputStream("techs.json");
        String data = gson.toJson(array);
        fos.write(data.getBytes());
        fos.close();
    }
}
