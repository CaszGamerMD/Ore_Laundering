package com.caszgamermd.orelaundering;

import java.util.ArrayList;
import java.util.List;

/**
 * One set of machine-status lines for Jade and WTHIT.
 * Reads the same block entity data that is synced to clients by update packets.
 * All tooltips identify their actual material, not just a liquid quantity.
 */
public final class OreMachineStatus {
    private OreMachineStatus() {}

    public static List<String> lines(OreMachineEntity machine) {
        List<String> text=new ArrayList<>(5);
        var values=machine.menuData;
        String material=machine.plantedOre().isEmpty()
                ? "None" : OreCompatibility.displayName(machine.plantedOre());

        if (machine.grows()) {
            text.add("Material: "+material);
            text.add("Growth: "+(machine.plantedOre().isEmpty() ? "Idle" :
                    machine.growthPercent()+"%"));
            text.add("Growth speed: "+(values.get(1)+1)+"x");
            text.add("Density upgrades: "+values.get(2)+"/3");
            text.add("Auto harvest: "+(values.get(3)==1?"ON":"OFF"));
            return text;
        }

        switch (machine.kind()) {
            case GRINDER -> {
                text.add("Material: "+material);
                text.add("Queued ore: "+values.get(5));
                text.add("Grinding: "+(values.get(5)==0?"Idle":
                        machine.processingPercent()+"%"));
                text.add("Grinding bits: "+values.get(4)+"/3");
            }
            case VAT -> {
                text.add("Material: "+material);
                text.add("Molten: "+machine.moltenMl()+" / 14,400 mL");
                text.add("Fuel below: "+values.get(10)+" mB");
                text.add(values.get(10)>0?"Ready to melt":"Needs lava fuel below");
            }
            case TANK -> {
                text.add("Material: "+material);
                text.add("Molten stored: "+machine.moltenMl()+" / 57,600 mL");
                text.add(machine.moltenMl()==0?"Empty":
                        machine.moltenMl()>=57600?"Full":"Ready to transfer");
            }
            case LAVA_TANK -> {
                text.add("Lava stored: "+machine.lavaMilliBuckets()+" / 16,000 mB");
                text.add(machine.lavaMilliBuckets()==0?"Empty":
                        machine.lavaMilliBuckets()>=16000?"Full":"Fueled");
            }
            case CASTING -> {
                int needed=machine.castingCapacityMl();
                text.add("Material: "+material);
                text.add(machine.castingBasin()?"Mode: Block basin":
                        needed==100?"Mode: Nugget mold":"Mode: Ingot mold");
                text.add("Molten: "+machine.moltenMl()+" / "+needed+" mL");
                int cooling=values.get(12);
                text.add(cooling>0?"Cooling: "+cooling+" ticks remaining":
                        !machine.getItem(1).isEmpty()?"Cast ready for collection":
                        machine.moltenMl()==0?"Waiting for molten input":"Filling");
            }
            case SPOUT -> {
                int code=values.get(20);
                text.add("Spout: "+(values.get(15)==1?"OPEN":"CLOSED"));
                text.add("Status: "+switch(code) {
                    case 1 -> "No molten source";
                    case 2 -> "No casting table / ore tank below";
                    case 3 -> "Target contains different material";
                    case 4 -> "Target full or blocked";
                    case 5 -> "Pouring";
                    default -> "Inactive";
                });
                text.add("Material: "+(machine.flowingOre().isEmpty()
                        ? "Not flowing" : OreCompatibility.displayName(machine.flowingOre())));
                text.add("Source: "+values.get(16)+" mL");
                text.add("Target: "+values.get(17)+" / "+values.get(18)+" mL");
            }
            default -> text.add("Status: Idle");
        }
        return text;
    }
}
