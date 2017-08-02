package cazador.furnaceoverhaul.tile;

import javax.annotation.Nullable;

import cazador.furnaceoverhaul.handler.EnumHandler.KitTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

public class TileEntityZenithFurnace extends TileEntityIronFurnace {
	
	public TileEntityZenithFurnace(){
	      super(KitTypes.ZENITH);
	    }
	
    @Override
    public int getCookTime(@Nullable ItemStack stack){
    	if(KitTypes.ZENITH != null){
    		
    	}
        return 1;
    }

    @Override
    public String getGuiID() {
    	return "furnaceoverhaul:zenithfurnace";
    }
}
