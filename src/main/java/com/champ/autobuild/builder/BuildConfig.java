package com.champ.autobuild.builder;

import java.util.ArrayList;
import java.util.List;





public final class BuildConfig {
    
    public double reach = 4.5;
    
    public int stuckTicks = 240;
    
    public boolean useScaffold = true;
    
    public boolean cleanupScaffold = true;
    
    public boolean breakObstructions = false;
    
    public boolean replaceExisting = false;
    
    public boolean humanMouse = false;
    
    public int humanClickDelay = 1;
    
    public List<String> scaffoldBlocks = new ArrayList<>(List.of("minecraft:dirt"));
    
    public String toolTier = "iron";
    
    public int maxBuildRounds = 6;
    
    public boolean verbose = false;
}
