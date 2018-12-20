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
            try {
                if (path.getFileName().toString().startsWith("README")) return;
                //System.out.println(path.getFileName());

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

        boolean add_event = false;

        Files.list(Paths.get("common/technology"))
        .filter(path->path.toString().endsWith(".txt"))
        .forEach((path) -> {
            try {
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

            if(tech.base_weight == 0 && tech.prerequisites.size() < 1 && !tech.is_start_tech) tech.is_event = true;
            if(tech.base_weight == 0 && !tech.key.equals("tech_colossus") && !tech.key.equals("tech_mine_living_metal") && !tech.is_start_tech) tech.is_event = true;
            if(tech.base_weight > 0 && tech.weight_modifiers.size() > 0 && tech.weight_modifiers.get(0).type == ModifierType.always && tech.weight_modifiers.get(0).factor == 0.0f) tech.is_event = true;

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

            for(Modifier m : tech.potential) {
                if(m.type.equals(ModifierType.is_gestalt)) {
                    if(gs(m.pair).equals("yes")) tech.is_gestalt = true;
                    else tech.is_gestalt = false;
                }
                if(m.type.equals(ModifierType.is_megacorp)) {
                    if(gs(m.pair).equals("yes")) tech.is_megacorp = true;
                    else tech.is_megacorp = false;
                }
                if(m.type.equals(ModifierType.is_machine_empire)) {
                    if(gs(m.pair).equals("yes")) tech.is_machine_empire = true;
                    else tech.is_machine_empire = false;
                }
                if(m.type.equals(ModifierType.is_hive_empire)) {
                    if(gs(m.pair).equals("yes")) tech.is_hive_empire = true;
                    else tech.is_hive_empire = false;
                }
                String str = m.toString();
                if(str.contains("Machine Intelligence Authority")) {
                    if(str.contains(" NOT have Machine Intelligence Authority")) {
                        tech.is_machine_empire = false;
                    } else {
                        tech.is_machine_empire = true;
                    }
                    if(str.contains("Has Government Civic: Driven Assimilator")) {
                        tech.is_drive_assimilator = true;
                    }
                    if(str.contains("Has Government Civic: Rogue Servitor")) {
                        tech.is_rogue_servitor = true;
                    }
                } else if (str.contains("Gestalt Consciousness Ethic")) {
                    if(str.contains(" NOT ")) {
                        tech.is_gestalt = false;
                    } else {
                        tech.is_gestalt = true;
                    }
                } else if (str.contains("Hive Mind Authority")) {
                    if(str.contains(" NOT ")) {
                        tech.is_hive_empire = false;
                    } else {
                        tech.is_hive_empire = true;
                    }
                }
            }
        }

        for(GameObject object : GameObject.values()) {
            visitUnlock(object);
        }

        Technology root = new Technology();
        root.tier = 0;

        Technology physics = new Technology();
        physics.tier = 0; physics.name = Area.physics.name(); physics.area = Area.physics;
        root.children.add(physics);

        Technology society = new Technology();
        society.tier = 0; society.name = Area.society.name(); society.area = Area.society;
        root.children.add(society);

        Technology engineering = new Technology();
        engineering.tier = 0; engineering.name = Area.engineering.name(); engineering.area = Area.engineering;
        root.children.add(engineering);

        for(Technology tech : technologies.values()) {
            if(tech.prerequisites.size() < 1) {
                if(tech.is_event && !add_event) continue;
                switch(tech.area) {
                    case physics: physics.children.add(tech); break;
                    case society: society.children.add(tech); break;
                    case engineering: engineering.children.add(tech); break;
                }
            } else {
                if(tech.is_event && !add_event) continue;
                String parent = tech.prerequisites.get(0);
                technologies.get(parent).children.add(tech);
            }
        }

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(WeightModifier.class, new WeightModifierTypeAdapter());
        builder.registerTypeAdapter(Modifier.class, new ModifierTypeAdapter());
        builder.setPrettyPrinting();
        Gson gson = builder.create();

        FileOutputStream fos = new FileOutputStream("techs.json");
        String data = gson.toJson(root);
        fos.write(data.getBytes());
        fos.close();
    }
}