package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreMachineMenu;
import com.caszgamermd.orelaundering.OreMachines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class OreMachineScreen extends AbstractContainerScreen<OreMachineMenu> {
    private static final int BACK = 0xff252b33, BORDER = 0xff667584,
        INNER = 0xff36434d, SLOT = 0xff111b22, TEXT = 0xffe8ecf2, MUTED = 0xffb2c1cb;
    public OreMachineScreen(OreMachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 214);
        inventoryLabelY = 102;
        titleLabelY = 7;
    }
    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(leftPos+x,topPos+y,leftPos+x+w,topPos+y+h,c);
    }
    private void label(GuiGraphicsExtractor g, String value, int x, int y, int col) {
        g.text(font, Component.literal(value), leftPos+x, topPos+y, col, false);
    }
    private void bar(GuiGraphicsExtractor g, int x, int y, int value, int max, int color) {
        panel(g, x, y, 145, 8, SLOT);
        if (max > 0) panel(g, x+1, y+1, Math.clamp(143*value/max,0,143), 6, color);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        panel(g,0,0,imageWidth,imageHeight,BORDER);
        panel(g,2,2,imageWidth-4,imageHeight-4,BACK);
        panel(g,8,20,160,77,INNER);
        for (int x : new int[]{43,79,115}) {
            panel(g,x-1,47,19,19,BORDER);
            panel(g,x,48,17,17,SLOT);
        }
        // Standard 9x3 player inventory plus hotbar.
        for (int row=0;row<3;row++) for (int col=0;col<9;col++) {
            panel(g,7+col*18,113+row*18,18,18,BORDER);
            panel(g,8+col*18,114+row*18,16,16,SLOT);
        }
        for (int col=0;col<9;col++) {
            panel(g,7+col*18,171,18,18,BORDER);
            panel(g,8+col*18,172,16,16,SLOT);
        }
        label(g,"INPUT",39,35,MUTED);
        label(g,"ORE",79,35,MUTED);
        label(g,"OUT",115,35,MUTED);
        int kind = menu.value(8);
        if (kind <= OreMachines.Kind.TROUGH.ordinal()) {
            int pct=menu.value(0);
            label(g,menu.value(11) == 0 ? "Not planted" : "Growth: "+pct+"%",10,72,TEXT);
            bar(g,10,84,pct,100,0xff70ba83);
            label(g,"Speed "+menu.value(1)+"/3    Density "+menu.value(2)+"/3"+
                (menu.value(3)!=0 ? "  AUTO" : ""),10,91,MUTED);
        } else if (kind==OreMachines.Kind.GRINDER.ordinal()) {
            label(g,"Queue: "+menu.value(5)+"  Precision: "+menu.value(4)+"/3",10,72,TEXT);
            bar(g,10,84,menu.value(5),64,0xffd3a65f);
        } else if (kind==OreMachines.Kind.VAT.ordinal()) {
            label(g,"Molten ore: "+menu.millilitres(6)+" / 14400 mL",10,72,TEXT);
            bar(g,10,84,menu.millilitres(6),14400,0xfff0aa55);
            label(g,menu.value(10)>0?"Lava below: "+menu.value(10)+" mB":"LAVA TANK REQUIRED BELOW",10,91,MUTED);
        } else if (kind==OreMachines.Kind.TANK.ordinal()) {
            label(g,"Stored ore: "+menu.millilitres(6)+" / 57600 mL",10,72,TEXT);
            bar(g,10,84,menu.millilitres(6),57600,0xffd4b174);
            label(g,"In: spout above  |  Out: spout beside",10,94,MUTED);
        } else if (kind==OreMachines.Kind.LAVA_TANK.ordinal()) {
            label(g,"Lava: "+menu.value(7)+" / 16000 mB",10,72,TEXT);
            bar(g,10,84,menu.value(7),16000,0xffe46c32);
        } else if (kind==OreMachines.Kind.SPOUT.ordinal()) {
            int status=menu.value(20);
            String state=switch(status) {
                case 0 -> "SPOUT CLOSED";
                case 1 -> "Needs molten Vat or Ore Tank";
                case 2 -> "No receiver below outlet";
                case 3 -> "Different material - blocked";
                case 4 -> "Full / cooling / invalid mold";
                default -> "POURING MOLTEN ORE";
            };
            label(g,state,10,70,status==5?TEXT:MUTED);
            label(g,"Source: "+menu.millilitres(16)+" mL",10,81,MUTED);
            int destination=menu.value(19);
            if(destination==1) {
                label(g,"Ore Tank: "+menu.millilitres(17)+"/57600 mL",10,92,MUTED);
            } else if(destination==2) {
                label(g,"Casting: "+menu.millilitres(17)+"/"+menu.millilitres(18)+" mL",10,92,MUTED);
            } else label(g,"Target: table or tank below",10,92,MUTED);
        } else if (kind==OreMachines.Kind.CASTING.ordinal()) {
            int held=menu.millilitres(6);
            int target=menu.value(14)==1?8100:(menu.value(13)>0?menu.value(13):900);
            String mode=menu.value(14)==1?"BASIN (BLOCK)":"TABLE (INGOT/NUGGET)";
            label(g,mode,10,71,TEXT);
            label(g,"Molten: "+held+" / "+target+" mL",10,81,MUTED);
            bar(g,10,93,held,target,0xfff0aa55);
            if(menu.value(12)>0) label(g,"Cooling: "+menu.value(12)+" ticks",10,101,TEXT);
        }
        label(g,"Input     |    Material    |    Output",8,196,MUTED);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Labels are drawn in extractBackground's absolute coordinate system.
    }
}
