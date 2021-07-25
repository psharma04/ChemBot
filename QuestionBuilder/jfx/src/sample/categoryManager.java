package sample;

import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class categoryManager {
    public static void checkDefs() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(("data/questions/"+"OutcomeDefs.txt"))));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        JSONObject defs = null;
        try {
            defs = new JSONObject(br.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
        br = null;

        File f = new File("data/questions/"+"OutcomeDefs.txt");
        long folders = 0;
        try {
            folders = Files.find(
                    Paths.get("data/questions/"),
                    1,  // how deep do we want to descend
                    (path, attributes) -> attributes.isDirectory()
            ).count() - 1;
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String, Object> temp = defs.toMap();
        HashMap<String, String> defMap = new HashMap<>();
        for (String s : temp.keySet()) {
            defMap.put(s, temp.get(s).toString().trim());
        }

        ArrayList<String> unknowns = new ArrayList<>();
        JSONObject index = new JSONObject();
        for (int i = 0; i < Integer.parseInt(Long.toString(folders)); i++) {
            br = null;
            try {
                br = new BufferedReader(new InputStreamReader(new FileInputStream(("data/questions/"+i + "/questionData.json"))));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            JSONObject obj = null;
            try {
                obj = new JSONObject(br.readLine());
                //System.out.println(obj);
            } catch (IOException e) {
                e.printStackTrace();
            }
            ArrayList<String> outcomeDefList = new ArrayList<>();
            String[] outcomes = obj.getJSONObject("data").getString("outcomes").trim().split(",");

            //System.out.println(Arrays.asList(outcomes));
            if (outcomes[0].equalsIgnoreCase("unk")) {
                //index.put(String.valueOf(i),"Not Defined");
                outcomeDefList.add("None Defined");
                //  System.out.println(i + " not defined");
            } else {
                if (outcomes.length > 1) {

                    //   System.out.println("question " + i + " has multiple outcomes");
                }
                for (int ii = 0; ii < outcomes.length; ii++) {
                    if (defMap.containsKey(outcomes[ii].trim())) {
                        outcomeDefList.add(defMap.get(outcomes[ii].trim()));
                        // System.out.println(defMap.get(outcomes[ii].trim()) + " is in the list");
                    } else {
                        // System.out.println(defMap.get(outcomes[ii]) + " is NOT in the list");
                        outcomeDefList.add("Unknown Outcome " + outcomes[ii]);
                        unknowns.add(outcomes[ii] + " " + i);
                    }
                }
                //System.out.println(outcomeDefList);
            }
            index.put(String.valueOf(i), outcomeDefList);
        }
        //System.out.println(index);
        if (unknowns.size() > 0) {
            System.out.println(unknowns);
            try {
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(("data/questions/"+"unknowns.txt"))));
                bw.write(unknowns.toString());
                bw.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(("data/questions/"+"question-outcomeIndex.json"))));
            bw.write(index.toString());
            bw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
