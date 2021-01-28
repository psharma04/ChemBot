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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

//todo
// add support for extended response questions with keyword analysis

//important
//may be necessary to have .contains checks for commands done as " keyword " instead of "keyword" to prevent triggering on words containing keyword

//important
//for now, text file content is having the objects written to them directly, however this is inefficient and should be changed later on,
// conserving data by copying the object data to text files while in memory

//150% zoom when taking screenshots for question data
public class Main {
    static int questionsAmt = 1;
    public static int backupMinutes = 15;
    public static boolean beta = false;
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

        ///////////user id  question ID     answered?
            HashMap<String, HashMap<String, Boolean>> questions = new HashMap<>();
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
        api.updateActivity(ActivityType.CUSTOM,"C.Help // ChemHelp");
        //api.getGroupChannelsByName()
            //System.out.println(api.createBotInvite());
            // Add a listener which marks with "Pong!" if someone writes "!ping"
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



                    if (event.getMessageContent().toLowerCase().startsWith("c.trivia")||event.getMessageContent().toLowerCase().startsWith("chemtrivia")) {
                        try {
                            String user = event.getMessageAuthor().getIdAsString();
                            long questionsAmt = Files.find(
                                    Paths.get("data/questions"),
                                    1,  // how deep do we want to descend
                                    (path, attributes) -> attributes.isDirectory()
                            ).count() - 1; // '-1' because '/tmp' is also counted in
                            String randomQ = String.valueOf((int)(Math.floor(Math.abs(Math.random()-0.01f)*questionsAmt)));
                            if(questions.containsKey(user)){
                                if(questions.get(user).containsKey(randomQ)){
                                    int count = 0;
                                    while(questions.get(user).containsKey(randomQ)){
                                        count++;
                                        randomQ = String.valueOf((int)(Math.floor(Math.abs(Math.random()-0.01f)*questionsAmt)));
                                        if (count>100) {
                                            break;
                                        }
                                    }
                                }
                            }
                            if(!event.getMessageContent().equalsIgnoreCase("c.trivia")&&!event.getMessageContent().equalsIgnoreCase("chemtrivia")) randomQ = String.valueOf(getAmtFromComplexString(event.getMessageContent())-1);
                            if(Integer.parseInt(randomQ)<0||Integer.parseInt(randomQ)>questionsAmt-1){
                                event.getChannel().sendMessage("Error selecting question: selected question ID does not exist! Picking a random question...");
                                randomQ = String.valueOf((int)(Math.floor(Math.abs(Math.random()-0.01f)*questionsAmt)));
                            }
                            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(("data/questions/"+randomQ+"/questionData.json"))));
                            JSONObject obj = new JSONObject(br.readLine());
                            String type = obj.getString("type");
                            String question = obj.getString("question");
                            boolean multi = false;
                            String answer = obj.getString("correct");
                            boolean showImage = false;
                            if(obj.getString("image").equalsIgnoreCase("y")){
                                //do some thing
                                showImage = true;
                            }
                            ArrayList<String> answers = new ArrayList<>();
                            if(type.equalsIgnoreCase("Multiple Choice")) {
                                multi = true;
                                JSONArray arr = obj.getJSONArray("answers");
                                answers.add(arr.getJSONObject(0).getString("A"));
                                answers.add(arr.getJSONObject(0).getString("B"));
                                answers.add(arr.getJSONObject(0).getString("C"));
                                answers.add(arr.getJSONObject(0).getString("D"));
                            }
                            boolean bmulti = false;
                            if (type.equalsIgnoreCase("BMultiple Choice")) {
                                multi = true;
                                bmulti = true;
                                JSONArray arr = obj.getJSONArray("answers");
                                answers.add(arr.getJSONObject(0).getString("A"));
                                answers.add(arr.getJSONObject(0).getString("B"));
                                answers.add(arr.getJSONObject(0).getString("C"));
                                answers.add(arr.getJSONObject(0).getString("D"));
                            }
                            int marks = obj.getInt("marks");
                            JSONObject extra = obj.getJSONObject("data");
                            String questionID = "Question N/A";
                            String paper = "Paper N/A";
                            String outcomes = "Outcomes N/A";
                            String band = "Band N/A";
                            String difficulty = "easy";
                            if (marks>=2) {
                                difficulty = "medium";
                                if(marks>=4){
                                    difficulty = "hard";
                                }
                            }
                            //important! Unhandled exceptions may cause issues later on
                            try{
                                questionID = extra.getString("questionID");
                            }catch(Exception e){}
                            try{
                                paper = extra.getString("paper");
                            }catch(Exception e){}
                            try{
                                outcomes = extra.getString("outcomes");
                            }catch(Exception e){}
                            try{
                                band = extra.getString("band");
                            }catch(Exception e){}
                            String answersList = "";
                            if(multi&&!bmulti) answersList = "A: "+answers.get(0)+"\nB: "+answers.get(1)+"\nC: "+answers.get(2)+"\nD: "+answers.get(3)+"\n\n";
                            System.out.println(question);
                            String FooterMessage = "You have " + 1.8f * marks + " minutes to answer!\nSource: " + paper + " | " + questionID + " | Outcomes " + outcomes + " | Band " + band + "\nID#" + (Integer.parseInt(randomQ) + 1);
                            if(!showImage) {
                                new MessageBuilder()
                                        .setEmbed(new EmbedBuilder()
                                                .setTitle(paper +" "+ questionID + " - " + marks + " mark(s)")
                                                .setDescription(question+"\n\n"+answersList)
                                                .setFooter(FooterMessage)
                                                .setColor(getDiffColor(difficulty)))
                                        .send(event.getChannel());
                            }else{
                                new MessageBuilder()
                                        .setEmbed(new EmbedBuilder()
                                                .setTitle(paper +" "+ questionID + " - " + marks + " mark(s)")
                                                .setDescription(question+"\n"+answersList)
                                                .setImage(new File("data/questions/"+randomQ+"/image.png"))
                                                .setFooter(FooterMessage)
                                                .setColor(getDiffColor(difficulty)))
                                        .send(event.getChannel());
                            }
                            String finalRandomQ = randomQ;
                            if(questions.containsKey(user)){
                                if(questions.get(user).containsKey(finalRandomQ)){
                                    questions.get(user).replace(finalRandomQ,false);
                                }else{
                                    questions.get(user).put(finalRandomQ,false);
                                }
                            }else{
                                questions.put(user, new HashMap<String, Boolean>());
                                questions.get(user).put(finalRandomQ,false);
                            }
                            boolean finalMulti = multi;
                            api.addMessageCreateListener(event2 -> {
                                //System.out.println("Detected message event!");
                                //System.out.println(event.getMessageAuthor().getIdAsString()+"\n"+event2.getMessageAuthor().getIdAsString());
                                if(event2.getMessageAuthor().getIdAsString().trim().equals(event.getMessageAuthor().getIdAsString().trim()) &&!questions.get(user).get(finalRandomQ)){
                                    //System.out.println("passed check 1");
                                    if(finalMulti) {
                                        //System.out.println("passed check 2");
                                        if (event2.getMessageContent().trim().equalsIgnoreCase(answer)) {
                                          //  System.out.println("passed check 3");
                                            questions.get(user).replace(finalRandomQ,true);
                                            event2.getChannel().sendMessage(":white_check_mark: Correct! Excellent job! (+" + marks + " marks)");
                                            if(event.isServerMessage()) {
                                                awardMarks(api, event2.getServer().get().getIdAsString(), event2.getMessageAuthor().getIdAsString(), marks);
                                            }
                                        }else{
                                            String s = "abcd";
                                            if(s.contains(event2.getMessageContent().toLowerCase().trim())){
                                                questions.get(user).replace(finalRandomQ,true);
                                                event2.getChannel().sendMessage(":x: Incorrect, the answer was "+answer+". Better luck next time!");
                                            }
                                        }
                                    }else{
                                        //do extended/short response keyword check method here
                                    }
                                }
                            }).removeAfter((long) (marks*60f*1.8f), TimeUnit.SECONDS)
                                    .addRemoveHandler(new Runnable() {
                                        @Override
                                        public void run() {
                                            System.out.println("Question timed out!");
                                            if(!questions.get(user).get(finalRandomQ)) {
                                                try {
                                                    event.getChannel().sendMessage("Time's up, "+api.getUserById(user).get().getMentionTag()+"! The answer was: " + answer);
                                                } catch (InterruptedException e) {
                                                    e.printStackTrace();
                                                } catch (ExecutionException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                            questions.remove(finalRandomQ);
                                        }
                                    });
                        } catch (IOException e) {
                            e.printStackTrace();

                        }
                        return;
                    }


                if((event.getMessageContent().equalsIgnoreCase("c.top")||event.getMessageContent().equalsIgnoreCase("chemtop"))&&event.isServerMessage()){
                    if(!ServersData.containsKey(event.getServer().get().getIdAsString())){
                        if(testForObject(event.getServer().get().getIdAsString())) {
                            ServersData.put(event.getServer().get().getIdAsString(), (HashMap<String, HashMap<String, String>>) getObjectFromAPI(bucketName, event.getServer().get().getIdAsString()));
                        }else{
                            createChannelProfile(api, event.getMessageAuthor().getIdAsString(), event.getServer().get().getIdAsString(),new HashMap<>());
                        }
                    }
                    String firstPlayer = "";
                    String secondPlayer = "";
                    String thirdPlayer = "";
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
                                int marks = Integer.parseInt(ServersData.get(event.getServer().get().getIdAsString()).get(u.getIdAsString()).get("marks"));
                                if (marks > firstValue) {
                                    thirdPlayer = secondPlayer;
                                    thirdValue = secondValue;
                                    secondPlayer = firstPlayer;
                                    secondValue = firstValue;
                                    firstPlayer = u.getDisplayName(event.getServer().get());
                                    firstValue = marks;
                                }else{
                                    if(marks>secondValue){
                                        thirdPlayer = secondPlayer;
                                        thirdValue = secondValue;
                                        secondPlayer = u.getDisplayName(event.getServer().get());
                                        secondValue = marks;
                                    }else{
                                        if(marks>thirdValue){
                                            thirdPlayer = u.getDisplayName(event.getServer().get());
                                            thirdValue = marks;
                                        }
                                    }
                                }
                        }
                    }
                    new MessageBuilder()
                            .setEmbed(new EmbedBuilder()
                            .setTitle(event.getServer().get().getName() + " Leaderboard:")
                            .addField("1. " + firstPlayer, firstValue+" marks")
                            .addField("2. " + secondPlayer, secondValue+" marks")
                            .addField("3. " + thirdPlayer, thirdValue+" marks")
                            ).send(event.getChannel());
                    return;
                }

                if((event.getMessageContent().equalsIgnoreCase("c.marks")||event.getMessageContent().equalsIgnoreCase("chemmarks"))){
                    if(event.isServerMessage()) {
                        awardMarks(api, event.getServer().get().getIdAsString(), event.getMessageAuthor().getIdAsString(), 0);
                        event.getChannel().sendMessage(":bookmark_tabs: You currently have **" + ServersData.get(event.getServer().get().getIdAsString()).get(event.getMessageAuthor().getIdAsString()).get("marks") + "** marks!");
                        return;
                    }else{
                        event.getChannel().sendMessage("Sorry! The marks feature is currently only supported in servers, and not private DMs.");
                    }
                }

                if(event.getMessageContent().toLowerCase().startsWith("c.help")||event.getMessageContent().toLowerCase().startsWith("chemhelp")){
                    if(event.getMessageContent().equalsIgnoreCase("c.help")||event.getMessageContent().equalsIgnoreCase("chemhelp")) {
                        new MessageBuilder()
                                .setEmbed(new EmbedBuilder()
                                        .setTitle("Commands List - ChemBot V0.0.5")
                                        .setDescription("*Use C.[Command] or Chem[Command] to send a command.\nFor example, C.Help opens this menu*" +
                                                "\n\n:grey_question: ``Help`` Try C.help help for more info!" +
                                                "\n\n:newspaper: ``Marks`` see how many marks you have" +
                                                "\n\n:page_facing_up: ``Test`` generate a customised test to complete (Not added, yet...)" +
                                                "\n\n:stopwatch: ``Top`` see who's at the top of the leaderboards" +
                                                "\n\n:test_tube: ``Trivia`` test your Chemistry knowledge with a random question" +
                                                "\n\n*The ChemistryBot Project and its code is open source and available online [here](https://github.com/iGamingMango/ChemBot)*")
                                        .setColor(Color.darkGray))
                                .send(event.getChannel());
                    }else{
                        if(event.getMessageContent().toLowerCase().contains("marks")){
                            new MessageBuilder()
                                    .setEmbed(new EmbedBuilder()
                                            .setTitle(":newspaper: ``C.Marks / ChemMarks``")
                                            .setDescription("This command will tell you how many marks you have accumulated on a server. " +
                                                    "Marks are awarded by completing trivia or test questions, the amount of marks you get will depend on the question." +
                                                    " The amount of time you have to complete a question is 1.8x[the number of marks], as is the official HSC standard. " +
                                                    "\n\nFor extended response questions, it may be a bit scary for time to type if you are using a phone, but you type faster " +
                                                    "than you think! On average, typing on a phone is 5 words per minute faster than writing, so just relax and use that extra " +
                                                    "time to make sure there are no typos!")
                                            .setColor(Color.darkGray))
                                    .send(event.getChannel());
                            return;
                        }

                        if(event.getMessageContent().toLowerCase().substring(8).contains("help")){
                            new MessageBuilder()
                                    .setEmbed(new EmbedBuilder()
                                            .setTitle(":grey_question: ``C.Help / ChemHelp``")
                                            .setDescription("Opens a helpful little menu!\nFurther information on commands can be retrieved by sending the ChemHelp or C.Help command, followed by another command or just its suffix.\n\nFor example, 'ChemHelp ChemMarks' and 'ChemHelp marks' will both bring up the *Marks* help menu")
                                            .setColor(Color.darkGray))
                                    .send(event.getChannel());
                            return;
                        }
                        if(event.getMessageContent().toLowerCase().contains("test")){
                            new MessageBuilder()
                                    .setEmbed(new EmbedBuilder()
                                            .setTitle(":page_facing_up: ``Test`` (Work In Progress)")
                                            .setDescription("Generates a test with a length of your choosing to complete.\n\nI want this to be really customisable and convenient, so please hang in there while I work on it. I'm hoping in the final version to have a big post-test summary that will help to identify you strengths and where more development is required!")
                                            .setColor(Color.darkGray))
                                    .send(event.getChannel());
                            return;
                        }
                        if(event.getMessageContent().toLowerCase().contains("top")){
                            new MessageBuilder()
                                    .setEmbed(new EmbedBuilder()
                                            .setTitle(":stopwatch: ``Top``")
                                            .setDescription("Shows the top 3 people with the most marks in the server.\n\nCurrently this does not work in DMs due to it being server-based and is intended as a bit of competitive fun. A more in-depth way of tracking your performance and progress that works in DMs will be implemented with *Tests*.")
                                            .setColor(Color.darkGray))
                                    .send(event.getChannel());
                            return;
                        }
                        if(event.getMessageContent().toLowerCase().contains("trivia")){
                            new MessageBuilder()
                                    .setEmbed(new EmbedBuilder()
                                            .setTitle(":test_tube: ``Trivia``")
                                            .setDescription("Trivia will give you a single random question from a set database to test your Chemistry knowledge on.\n\n" +
                                                    "The additional text at the bottom has more information on the question:\n" +
                                                    "__'Source: ABC Paper'__ shows the paper where the question came from.\n" +
                                                    "__'Question X'__ shows the corresponding question number in the paper.\n" +
                                                    "__'Outcomes CHXY-Z'__ shows the syllabus outcomes the question is examining.\n" +
                                                    "__'Band X'__ shows the band-level student the question is aimed at.\n" +
                                                    "__'ID#XYZ'__ the question ID, **typing 'C.Trivia 3' always shows the question with ID 3.**")
                                            .setColor(Color.darkGray))
                                    .send(event.getChannel());
                            return;
                        }
                    }
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
                        if(content.toLowerCase().startsWith("take")&&(content.toLowerCase().contains("marks")||content.toLowerCase().contains("mark"))){
                            int amount = getAmtFromComplexString(content);
                            for (User u: event.getMessage().getMentionedUsers()) {
                                if(event.getServer().get().getMembers().contains(u)){
                                    awardMarks(api,event.getServer().get().getIdAsString(),u.getIdAsString(),-amount);
                                }
                            }
                            ArrayList<String> users = new ArrayList<>();
                            for (User u: event.getMessage().getMentionedUsers()) {
                                users.add(String.valueOf(u.getDisplayName(event.getServer().get())));
                            }
                            event.getChannel().sendMessage("taken "+ amount + " answer(s) away from " + users);
                        }

                        if(content.toLowerCase().startsWith("give")&&(content.toLowerCase().contains("marks")||content.toLowerCase().contains("mark"))){
                            int amount = getAmtFromComplexString(content);
                            for (User u: event.getMessage().getMentionedUsers()) {
                                if(event.getServer().get().getMembers().contains(u)){
                                    awardMarks(api,event.getServer().get().getIdAsString(),u.getIdAsString(),amount);
                                }
                            }
                            ArrayList<String> users = new ArrayList<>();
                            for (User u: event.getMessage().getMentionedUsers()) {
                                users.add(String.valueOf(u.getDisplayName(event.getServer().get())));
                            }
                            event.getChannel().sendMessage("given "+ amount + " marks to " + users);
                        }
                    }
                }

            }});
            backupState.scheduleAtFixedRate(backup,backupMinutes*60L*1000L,backupMinutes*60L*1000L);
    }

    public static int getMarks(String Server, String User){
        if(ServersData.containsKey(Server)){
            if(ServersData.get(Server).containsKey(User)){
                if(ServersData.get(Server).get(User).containsKey("marks")){
                    return Integer.parseInt(ServersData.get(Server).get(User).get("marks"));
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

    public static void awardMarks(DiscordApi api, String serverID, String UserID, int amount){
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
                //System.out.println("user is present in usermap, updating marks");
                int temp = Integer.parseInt(ServersData.get(serverID).get(UserID).get("marks"));
                temp+=amount;
                ServersData.get(serverID).get(UserID).replace("marks",String.valueOf(temp));
            }else{
                //System.out.println("user not present in usermap, attempting to add new user and assign marks");
                HashMap<String,String> temp = new HashMap<>();
                temp.put("marks",String.valueOf(amount));
                createUserProfile(UserID,serverID,temp);
            }
        }else{
            //System.out.println("not in servermap, checking if server is present in API");
            if(testForObject(serverID)){
                //System.out.println("located server in API, adding to servermap and incrementing oreos");
                ServersData.put(serverID, (HashMap<String, HashMap<String, String>>) getObjectFromAPI(bucketName, serverID));
                int temp = Integer.parseInt(ServersData.get(serverID).get(UserID).get("marks"));
                temp+=amount;
                ServersData.get(serverID).get(UserID).replace("marks",String.valueOf(temp));
            }else{
                //System.out.println("server not present in API, creating new API entry");
                HashMap<String, String> tempMap = new HashMap<>();
                tempMap.putIfAbsent("marks", String.valueOf(amount));
                createChannelProfile(api, UserID, serverID, tempMap);
            }
        }
    }

    public static void createUserProfile(String UserID, String serverID, HashMap<String, String> userData){
        HashMap<String,String> tempMap = userData;
        tempMap.putIfAbsent("marks","0");
        ServersData.get(serverID).put(UserID, tempMap);
        forceBackup(serverID);
    }

    public static void createChannelProfile(DiscordApi api, String UserID, String serverID, HashMap<String, String> userData){
        //System.out.println("Attempting to create channel profile in servermap");
        TempKeyData = new HashMap<>();
        TempUserData = new HashMap<>();
        HashMap<String,String> tempMap = userData;
        tempMap.putIfAbsent("marks","0");
        TempUserData.put(UserID, tempMap);
        ServersData.put(serverID, TempUserData);
        //System.out.println("populating servermap server with generated users");
        for (User u : api.getServerById(serverID).get().getMembers()) {
            if(!Objects.equals(u.getIdAsString().trim(), UserID.trim())) {
                createUserProfile(u.getIdAsString(), serverID, new HashMap<>());
            }else{
                //System.out.println("Generator User, skipping");
            }
        }
        //System.out.println("Users generated, sending off data structure to API");
        writeObjectToAPI(ServersData.get(serverID), bucketName, serverID);
    }



}