package me.inertia.chembot;
import com.ibm.cloud.objectstorage.SDKGlobalConfiguration;
import com.ibm.cloud.objectstorage.SdkClientException;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.channel.GroupChannel;
import org.javacord.api.entity.intent.Intent;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.MessageDecoration;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.entity.user.UserStatus;
import org.jsoup.Jsoup;

import java.awt.*;
import java.io.*;
import java.net.*;
import org.json.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.model.Bucket;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsRequest;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectListing;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;

import static me.inertia.chembot.APIMethods.*;
//important
//may be necessary to have .contains checks for commands done as " keyword " instead of "keyword" to prevent triggering on words containing keyword

//important
//for now, text file content is having the objects written to them directly, however this is inefficient and should be changed later on,
// conserving data by copying the object data to text files while in memory

public class Main {
    public static int backupMinutes = 15;
    public static boolean beta = true;
    public static boolean debug = false;
    public static ArrayList<String> logOnChannel = new ArrayList<>();

    public static Color getDiffColor(String diff){
        if(Objects.equals(diff, "easy")){
            return Color.blue;
        }
        if(Objects.equals(diff, "medium")){
            return Color.yellow;
        }
        if(Objects.equals(diff, "hard")){
            return Color.red;
        }
        return Color.black;
    }
    static AmazonS3 _cos;
    static String bucketName = "chembucket";
    private static String COS_ENDPOINT = "s3.au-syd.cloud-object-storage.appdomain.cloud"; // eg "https://s3.us.cloud-object-storage.appdomain.cloud"
    private static String COS_API_KEY_ID = getCloudAPI(); // eg "0viPHOY7LbLNa9eLftrtHPpTjoGv6hbLD1QalRXikliJ"
    private static String COS_AUTH_ENDPOINT = "https://iam.cloud.ibm.com/identity/token";
    private static String COS_SERVICE_CRN = "crn:v1:bluemix:public:cloud-object-storage:global:a/2654c4e500c94a13b20d67dc294e8b7d:d748a6b1-d96d-491f-8bae-df5874643615"; // "crn:v1:bluemix:public:cloud-object-storage:global:a/<CREDENTIAL_ID_AS_GENERATED>:<SERVICE_ID_AS_GENERATED>::"
    private static String COS_BUCKET_LOCATION = "au-syd"; // eg "us"

                        //server          user           dataKey  value
    public static HashMap<String, HashMap<String, HashMap<String,String>>> ServersData = new HashMap<>();

    //                    user           dataKey  value
    public static HashMap<String, HashMap<String,String>> TempUserData = new HashMap<>();

                       //datakey  value
    public static HashMap<String,String> TempKeyData = new HashMap<>();



    static Timer backupState = new Timer();
    static TimerTask backup = new TimerTask() {
        @Override
        public void run() {
            //System.out.println("Backing up data!");
            for (String s :ServersData.keySet()) {
                writeObjectToAPI(ServersData.get(s),bucketName,s);
            }
        }
    };

    public static void forceBackup(){
        for (String s :ServersData.keySet()) {
            writeObjectToAPI(ServersData.get(s),bucketName,s);
        }
    }

    public static void forceBackup(String ServerID){
            writeObjectToAPI(ServersData.get(ServerID),bucketName,ServerID);
    }

    public static String adminID = "258695315299893259";
    public static void main(String[] args) {
        if(beta){
            bucketName = "chembucketbeta";
        }
        //region IBM API Code
        SDKGlobalConfiguration.IAM_ENDPOINT = COS_AUTH_ENDPOINT;

        try {
           _cos = createClient(COS_API_KEY_ID, COS_SERVICE_CRN, COS_ENDPOINT, COS_BUCKET_LOCATION);
        } catch (SdkClientException sdke) {
            System.out.printf("SDK Error: %s\n", sdke.getMessage());
        } catch (Exception e) {
            System.out.printf("Error: %s\n", e.getMessage());
        }


        //endregion

            HashMap<String, Boolean> trivias = new HashMap<>();
            HashMap<String, ArrayList<String>> triviaBlackList = new HashMap<>();
            // Log the bot in
        DiscordApi api;
        if(!beta) {
            //System.out.println("Starting in Release Mode");
            api = new DiscordApiBuilder()
                    .setToken(getDiscordAPI(false))
                    .setAllIntents()
                    .login()
                    .join();

        }else{
            //System.out.println("Starting in Beta Mode");
            api = new DiscordApiBuilder()
                    .setToken(getDiscordAPI(true))
                    .setAllIntents()
                    .login()
                    .join();
        }
        api.updateActivity(ActivityType.CUSTOM,"C!help");
        //api.getGroupChannelsByName()
            ////System.out.println(api.createBotInvite());
            // Add a listener which answers with "Pong!" if someone writes "!ping"
            api.addMessageCreateListener(event -> {


                /* Event Template
                if(event.getMessageContent().equalsIgnoreCase("condition")&&event.isServerMessage()){
                    event.getChannel().sendMessage("response");
                    return;
                }
                 */


                if(event.getMessageAuthor().isUser()){
                    boolean log = false;
                    if(logOnChannel.contains(event.getChannel().getIdAsString())){
                        log = true;
                    }



                    if (event.getMessageContent().equalsIgnoreCase("jtrivia")) {
                        try {
                            URL u = new URL("https://opentdb.com/api.php?amount=1");
                            HttpURLConnection connection = (HttpURLConnection) u.openConnection();
                            // open the stream and put it into BufferedReader
                            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                            String s = br.readLine();
                            //System.out.println(s);
                            JSONObject obj = new JSONObject(s);
                            JSONArray trivia = obj.getJSONArray("results");
                            String question = Jsoup.parse(trivia.getJSONObject(0).getString("question")).text();
                            String difficulty = Jsoup.parse(trivia.getJSONObject(0).getString("difficulty")).text();
                            String type = Jsoup.parse(trivia.getJSONObject(0).getString("type")).text();
                            String answer = Jsoup.parse(trivia.getJSONObject(0).getString("correct_answer")).text();
                            System.out.println("Constructed "+type+" trivia question for "+event.getMessageAuthor()+" asking \"" +question+ "\" with the answer" + answer);
                            trivias.put(answer,true);
                            ArrayList<String> strList = new ArrayList<>();
                            ArrayList<String> stringList2 = new ArrayList<>();
                            HashMap<Integer,String> strMap = new HashMap<>();
                            if(!Objects.equals(type, "multiple")) {
                                strList.add("true");
                                strList.add("false");
                                if(Objects.equals(type, "boolean")){
                                    type = "true or false?";
                                }
                                new MessageBuilder()
                                        .setEmbed(new EmbedBuilder()
                                                .setTitle(difficulty + " - " + type)
                                                .setDescription("Riddle me this: " + question)
                                                .setFooter("Everyone has ONE MINUTE to answer!")
                                                .setColor(getDiffColor(difficulty)))
                                        .send(event.getChannel());
                            }else{
                                List<Object> objects = trivia.toList();
                                String str = objects.get(0).toString();
                                String incorrect = Jsoup.parse(str.substring(str.indexOf("incorrect_answers=[")+19,str.indexOf(']',str.indexOf("incorrect_answers=[")))).text();
                                List<String> incorrectList = Arrays.asList(incorrect.split(","));
                                strList = new ArrayList<>(incorrectList);
                                strList.add(answer);
                                Collections.shuffle(strList);
                                stringList2.addAll(strList);

                                if(stringList2.size()>0) {
                                    for (int i = 0; i < stringList2.size(); i++) {
                                        String temp = stringList2.get(i);
                                        strMap.put(i,temp);
                                        stringList2.set(i," "+(i+1)+") "+temp);
                                    }
                                }
                                new MessageBuilder()
                                        .setEmbed(new EmbedBuilder()
                                                .setTitle(difficulty + " - " + type)
                                                .setDescription("Riddle me this: " + question + "\n" + stringList2)
                                                .setFooter("Everyone has ONE MINUTE to answer!")
                                                .setColor(getDiffColor(difficulty)))
                                        .send(event.getChannel());
                            }
                            ArrayList<String> finalStrList = strList;
                            triviaBlackList.putIfAbsent(answer,new ArrayList<>());
                            boolean finalLog = log;
                            api.addMessageCreateListener(event2 -> {
                                int tempi = 0;
                                for (String string : finalStrList) {
                                    boolean pass = false;
                                    if((event2.getMessageContent().trim().equalsIgnoreCase(string.trim()))) pass = true;
                                    try {
                                        if (strMap.containsKey(Integer.parseInt(event2.getMessageContent().trim())-1))
                                            pass = true;
                                    }catch(Exception e){System.out.print("");}
                                    if (pass&&!triviaBlackList.get(answer).contains(event2.getMessageAuthor().getIdAsString())) {
                                        pass = false;
                                        if(event2.getMessageContent().trim().equalsIgnoreCase(answer.trim())) pass = true;
                                        try{
                                            if(strMap.get((Integer.parseInt(event2.getMessageContent()))-1).trim()==answer.trim()) pass = true;
                                        }catch(Exception e){}
                                        if (pass && trivias.get(answer)) {
                                            trivias.replace(answer, false);
                                            //System.out.println(difficulty);
                                            int rewardMultiplier = 0;
                                            if (difficulty.equalsIgnoreCase("easy")) {
                                                rewardMultiplier = 2;
                                            }
                                            if (difficulty.equalsIgnoreCase("medium")) {
                                                rewardMultiplier = 8;
                                            }
                                            if (difficulty.equalsIgnoreCase("hard")) {
                                                rewardMultiplier = 32;
                                            }
                                            int random = (int) ( (Math.random()) * (float)rewardMultiplier + 1f);
                                            if(event2.isServerMessage()) {
                                                Long l = null;
                                                if(finalLog) {
                                                    l = System.currentTimeMillis();
                                                    System.out.println("Awarding Oreos");
                                                }
                                                awardOreos(api,event2.getServer().get().getIdAsString(), event2.getMessageAuthor().getIdAsString(), random);
                                                if(finalLog) {
                                                    System.out.println("Completed awarding Oreos, took "+(System.currentTimeMillis()-l)+"ms");
                                                    System.out.println(ServersData);
                                                }
                                            }
                                            new MessageBuilder()
                                                    .append(event2.getMessageAuthor().getDisplayName(), MessageDecoration.BOLD)
                                                    .append(" is correct and has been awarded "+random+" Oreos! Great Job!")
                                                    .send(event2.getChannel());
                                        } else {
                                            if(trivias.get(answer)) {
                                                triviaBlackList.get(answer).add(event2.getMessageAuthor().getIdAsString());
                                                new MessageBuilder()
                                                        .append("Incorrect! Better luck next time!")
                                                        .send(event2.getChannel());
                                            }
                                        }
                                    }else {
                                        boolean pass2 = false;
                                        try{
                                            if (strMap.get((Integer.parseInt(event2.getMessageContent())) - 1).trim() == answer.trim()) {
                                                pass2 = true;
                                            }
                                        }catch(Exception e){
                                            System.out.print("");
                                        }
                                        if((event2.getMessageContent().trim().equalsIgnoreCase(string.trim()))) {
                                            new MessageBuilder()
                                                    .append("You've already guessed! Try again next time!")
                                                    .send(event2.getChannel());
                                        }
                                    }
                                }
                            }).removeAfter(1, TimeUnit.MINUTES)
                                    .addRemoveHandler(new Runnable() {
                                        @Override
                                        public void run() {
                                            if(trivias.get(answer)) {
                                                event.getChannel().sendMessage("Time's up! The answer was: " + answer);
                                            }
                                            triviaBlackList.remove(answer);
                                            trivias.remove(answer);
                                        }
                                    });

                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();

                        }
                        return;
                    }


                    if(event.getMessageContent().toLowerCase().startsWith("jpay")&&event.isServerMessage()){
                        int amount = getAmtFromComplexString(event.getMessageContent());
                        int userBal = getOreos(event.getServer().get().getIdAsString(),event.getMessageAuthor().getIdAsString());
                        boolean ranOut = false;
                        ArrayList<String> users = new ArrayList<>();
                        for (User u: event.getMessage().getMentionedUsers()) {
                            if(event.getServer().get().getMembers().contains(u)){
                                if(userBal>=amount) {
                                    awardOreos(api, event.getServer().get().getIdAsString(), u.getIdAsString(), amount);
                                    awardOreos(api, event.getServer().get().getIdAsString(), event.getMessageAuthor().getIdAsString(), -amount);
                                    users.add(String.valueOf(u.getDisplayName(event.getServer().get())));
                                }else{
                                    ranOut = true;
                                }
                            }
                        }
                        if(ranOut){
                            if(users.size()>0) {
                                event.getChannel().sendMessage("ran out of oreos to give, but still gave " + amount + " oreos to " + users + "!");
                            }else{
                                event.getChannel().sendMessage("You can't afford to give any oreos away :(");
                            }
                        }else {
                            event.getChannel().sendMessage("given " + amount + " oreos to " + users);
                        }
                        return;
                    }


                if(event.getMessageContent().equalsIgnoreCase("jtop")&&event.isServerMessage()){
                    if(!ServersData.containsKey(event.getServer().get().getIdAsString())){
                        if(testForObject(event.getServer().get().getIdAsString())) {
                            ServersData.put(event.getServer().get().getIdAsString(), (HashMap<String, HashMap<String, String>>) getObjectFromAPI(bucketName, event.getServer().get().getIdAsString()));
                        }else{
                            createChannelProfile(api, event.getMessageAuthor().getIdAsString(), event.getServer().get().getIdAsString(),new HashMap<>());
                        }
                    }
                    String firstPlayer = "";
                    String secondPlayer = "";
                    String thirdPlayer = "Your mum";
                    int firstValue = 0;
                    int secondValue = 0;
                    int thirdValue = 0;
                    //awardOreos(api, event.getServer().get().getIdAsString(),api.getYourself().getIdAsString(),0);
                    createUserProfile(api.getYourself().getIdAsString(),event.getServer().get().getIdAsString(),new HashMap<>());
                    TempUserData = ServersData.get(event.getServer().get().getIdAsString());
                    for (User u: event.getServer().get().getMembers()) {
//                        System.out.println(ServersData);
//                        System.out.println(event.getServer().get().getMembers());
                        String user = u.getIdAsString();
                        //System.out.println(event.getServer().get().getMemberCount());
                        if(TempUserData.containsKey(user)) {
                           // System.out.println(u.getDisplayName(event.getServer().get()));
                                int oreos = Integer.parseInt(ServersData.get(event.getServer().get().getIdAsString()).get(u.getIdAsString()).get("oreos"));
                                if (oreos > firstValue) {
                                    thirdPlayer = secondPlayer;
                                    thirdValue = secondValue;
                                    secondPlayer = firstPlayer;
                                    secondValue = firstValue;
                                    firstPlayer = u.getDisplayName(event.getServer().get());
                                    firstValue = oreos;
                                }else{
                                    if(oreos>secondValue){
                                        thirdPlayer = secondPlayer;
                                        thirdValue = secondValue;
                                        secondPlayer = u.getDisplayName(event.getServer().get());
                                        secondValue = oreos;
                                    }else{
                                        if(oreos>thirdValue){
                                            thirdPlayer = u.getDisplayName(event.getServer().get());
                                            thirdValue = oreos;
                                        }
                                    }
                                }
                        }
                    }
                    new MessageBuilder()
                            .setEmbed(new EmbedBuilder()
                            .setTitle(event.getServer().get().getName() + " Leaderboard:")
                            .addField("1. " + firstPlayer, firstValue+" Oreos")
                            .addField("2. " + secondPlayer, secondValue+" Oreos")
                            .addField("3. " + thirdPlayer, thirdValue+" Oreos")
                            ).send(event.getChannel());
                    return;
                }

                if(event.getMessageContent().equalsIgnoreCase("joreos")&&event.isServerMessage()){
                    awardOreos(api, event.getServer().get().getIdAsString(),event.getMessageAuthor().getIdAsString(),0);
                    event.getChannel().sendMessage(":cookie: You currently have **"+ServersData.get(event.getServer().get().getIdAsString()).get(event.getMessageAuthor().getIdAsString()).get("oreos")+"** Oreos!");
                    return;
                }

                if(event.getMessageContent().equalsIgnoreCase("jhelp")){
                    new MessageBuilder()
                            .setEmbed(new EmbedBuilder()
                                    .setTitle("Help Menu - JuniorBot V0.0.4")
                                    .setDescription("``jHelp`` opens this helpful little menu!" +
                                            "\n\n``jOreos`` see how many :cookie: you have" +
                                            "\n\n``jPay`` share the happiness!" +
                                            "\n\n``jTop`` see who's at the top of the leaderboards" +
                                            "\n\n``jTrivia`` test your trivia knowledge")
                                    .setColor(Color.darkGray))
                            .send(event.getChannel());
                    return;
                }
                if(event.getMessageAuthor().getIdAsString().contentEquals(adminID)){
                    String content = event.getMessageContent();
                    if(log) System.out.println("passed ID check");
                    if(!debug) {
                        if(log) System.out.println("not in debug mode");
                        if(content.toLowerCase().startsWith("enable")) {
                            if (content.toLowerCase().contains("debug") || content.toLowerCase().contains("debugging")) {
                                event.getChannel().sendMessage("Access Granted, enabling debug mode");
                                if (log) System.out.println("enabling debug mode");
                                debug = true;
                                if (content.toLowerCase().contains("log") || content.toLowerCase().contains("logging")) {
                                    event.getChannel().sendMessage("Enabling logging for this channel");
                                    if (!logOnChannel.contains(event.getChannel().getIdAsString())) {
                                        logOnChannel.add(event.getChannel().getIdAsString());
                                    }
                                }
                            }
                        }
                    }else{


                        if(content.toLowerCase().startsWith("enable")) {
                            if (content.toLowerCase().contains("log") || content.toLowerCase().contains("logging")) {
                                event.getChannel().sendMessage("Enabling logging for this channel");
                                if (!logOnChannel.contains(event.getChannel().getIdAsString())) {
                                    logOnChannel.add(event.getChannel().getIdAsString());
                                }
                            }
                        }
                        if(content.toLowerCase().startsWith("disable")){
                            if(content.toLowerCase().contains("debug") || content.toLowerCase().contains("debugging")){
                                if(!log) {
                                    event.getChannel().sendMessage("Understood, disabling debug mode");
                                }else{
                                    event.getChannel().sendMessage("Understood, disabling debug mode and safety disabling logging");
                                    logOnChannel.remove(event.getChannel().getIdAsString());
                                }
                                debug = false;
                            }
                            if (content.toLowerCase().contains("log") || content.toLowerCase().contains("logging")) {
                                event.getChannel().sendMessage("Disabling logging for this channel");
                                if (logOnChannel.contains(event.getChannel().getIdAsString())) {
                                    logOnChannel.remove(event.getChannel().getIdAsString());
                                }
                            }
                        }
                        if(content.toLowerCase().startsWith("take")&&(content.toLowerCase().contains("oreos")||content.toLowerCase().contains("oreo"))){
                            int amount = getAmtFromComplexString(content);
                            for (User u: event.getMessage().getMentionedUsers()) {
                                if(event.getServer().get().getMembers().contains(u)){
                                    awardOreos(api,event.getServer().get().getIdAsString(),u.getIdAsString(),-amount);
                                }
                            }
                            ArrayList<String> users = new ArrayList<>();
                            for (User u: event.getMessage().getMentionedUsers()) {
                                users.add(String.valueOf(u.getDisplayName(event.getServer().get())));
                            }
                            event.getChannel().sendMessage("taken "+ amount + " oreos away from " + users);
                        }

                        if(content.toLowerCase().startsWith("give")&&(content.toLowerCase().contains("oreos")||content.toLowerCase().contains("oreo"))){
                            int amount = getAmtFromComplexString(content);
                            for (User u: event.getMessage().getMentionedUsers()) {
                                if(event.getServer().get().getMembers().contains(u)){
                                    awardOreos(api,event.getServer().get().getIdAsString(),u.getIdAsString(),amount);
                                }
                            }
                            ArrayList<String> users = new ArrayList<>();
                            for (User u: event.getMessage().getMentionedUsers()) {
                                users.add(String.valueOf(u.getDisplayName(event.getServer().get())));
                            }
                            event.getChannel().sendMessage("given "+ amount + " oreos to " + users);
                        }
                    }
                }

            }});
            backupState.scheduleAtFixedRate(backup,backupMinutes*60L*1000L,backupMinutes*60L*1000L);
    }

    public static int getOreos(String Server, String User){
        if(ServersData.containsKey(Server)){
            if(ServersData.get(Server).containsKey(User)){
                if(ServersData.get(Server).get(User).containsKey("oreos")){
                    return Integer.parseInt(ServersData.get(Server).get(User).get("oreos"));
                }
            }
        }
        return 0;
    }

    public static int getAmtFromComplexString(String s){
        int index = 0;
        char last = '0';
        int length = 0;
        boolean digit = false;
        boolean lastdigit = false;
        boolean prefixLetter = false;
        for (char c:s.toCharArray()) {
            lastdigit = digit;
            digit = Character.isDigit(c);
            if(digit&&lastdigit){
                length++;
            }else{
                if(!lastdigit){
                    if(last==' ') {
                        prefixLetter = false;
                        if (digit) {
                            length = 1;
                        }
                    }else{
                        prefixLetter = true;
                    }
                }
                if(!digit&&lastdigit){
                    if(c!=' '||prefixLetter){
                        length = 0;
                    }else{
                        break;
                    }
                }
            }
            index++;
            last = c;
        }
        if(length == 0){
            return 0;
        }else {
            //System.out.println(s);
            char[] temp= new char[s.length()];
            s.getChars(index-length, index,temp,0);
            return Integer.parseInt(String.valueOf(Integer.parseInt(String.valueOf(temp).trim())));
        }
    }

    public static void awardOreos(DiscordApi api, String serverID, String UserID, int amount){
        try {
            if(api.getUserById(UserID).get().isBot() && UserID != api.getYourself().getIdAsString()) return;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        //System.out.println("checking if server is in master servermap");
        if(ServersData.containsKey(serverID)){
            //System.out.println("server is present in servermap, checking if user is present in usermap");
            if(ServersData.get(serverID).containsKey(UserID)){
                //System.out.println("user is present in usermap, updating oreos");
                int temp = Integer.parseInt(ServersData.get(serverID).get(UserID).get("oreos"));
                temp+=amount;
                ServersData.get(serverID).get(UserID).replace("oreos",String.valueOf(temp));
            }else{
                //System.out.println("user not present in usermap, attempting to add new user and assign oreos");
                HashMap<String,String> temp = new HashMap<>();
                temp.put("oreos",String.valueOf(amount));
                createUserProfile(UserID,serverID,temp);
            }
        }else{
            //System.out.println("not in servermap, checking if server is present in API");
            if(testForObject(serverID)){
                //System.out.println("located server in API, adding to servermap and incrementing oreos");
                ServersData.put(serverID, (HashMap<String, HashMap<String, String>>) getObjectFromAPI(bucketName, serverID));
                int temp = Integer.parseInt(ServersData.get(serverID).get(UserID).get("oreos"));
                temp+=amount;
                ServersData.get(serverID).get(UserID).replace("oreos",String.valueOf(temp));
            }else{
                //System.out.println("server not present in API, creating new API entry");
                HashMap<String, String> tempMap = new HashMap<>();
                tempMap.putIfAbsent("oreos", String.valueOf(amount));
                createChannelProfile(api, UserID, serverID, tempMap);
            }
        }
    }

    public static void addUserElement(){

    }

    public static void createUserProfile(String UserID, String serverID, HashMap<String, String> userData){
        HashMap<String,String> tempMap = userData;
        tempMap.putIfAbsent("oreos","0");
        ServersData.get(serverID).put(UserID, tempMap);
        forceBackup(serverID);
    }

    public static void createChannelProfile(DiscordApi api, String UserID, String serverID, HashMap<String, String> userData){
        //System.out.println("Attempting to create channel profile in servermap");
        TempKeyData = new HashMap<>();
        TempUserData = new HashMap<>();
        HashMap<String,String> tempMap = userData;
        tempMap.putIfAbsent("oreos","0");
        TempUserData.put(UserID, tempMap);
        ServersData.put(serverID, TempUserData);
        //System.out.println("populating servermap server with generated users");
        for (User u : api.getServerById(serverID).get().getMembers()) {
            if(u.getIdAsString()!=UserID) {
                createUserProfile(u.getIdAsString(), serverID, new HashMap<>());
            }else{
                //System.out.println("Generator User, skipping");
            }
        }
        //System.out.println("Users generated, sending off data structure to API");
        writeObjectToAPI(ServersData.get(serverID), bucketName, serverID);
    }



}