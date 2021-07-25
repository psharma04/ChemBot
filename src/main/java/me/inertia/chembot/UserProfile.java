package me.inertia.chembot;

import java.io.File;
import java.util.HashMap;
import java.util.Set;

public class UserProfile {
    String userID;
    Set<String> outcomes = Main.outcomeIDs;
    //      Outcome       Statistic Value
    HashMap<String, HashMap<String,String>> OutcomeStatistics = new HashMap<>();
    public UserProfile(String userID){

    }
    public void updateOutcomes(){
        Main.updateOutcomeSet();
        outcomes = Main.outcomeIDs;
    }
}
