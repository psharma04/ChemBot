package me.inertia.chembot;

import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.CertainMessageEvent;

import java.util.ArrayList;
import java.util.HashMap;

import static me.inertia.chembot.Main.getAmtFromComplexString;
import static me.inertia.chembot.Main.userQManager;

class QuestionsUserInstance {
    QuestionInstance questionInstance;
    String user;
    //      QID     QInstance
    ArrayList<QuestionInstance> questions = new ArrayList<>();

    public QuestionsUserInstance(String user) {
        this.user = user;
        userQManager.putIfAbsent(user,this);
    }

    public void createQuestion(CertainMessageEvent event){
        long questionsAmt = Main.getQuestionsAmount();
        String randomQ = String.valueOf((int)(Math.floor(Math.abs(Math.random()-0.01f)*questionsAmt)));
        String user = event.getMessageAuthor().getIdAsString();
        questions.add(new QuestionInstance(this, randomQ, event));
    }

    public void createQuestion(CertainMessageEvent event, String ID){
        String randomQ = String.valueOf(getAmtFromComplexString(event.getMessageContent())-1);
        long questionsAmt = Main.getQuestionsAmount();
        if(Integer.parseInt(randomQ)<0||Integer.parseInt(randomQ)>questionsAmt-1){
            event.getChannel().sendMessage("Error selecting question: selected question ID does not exist! Picking a random question...");
            randomQ = String.valueOf((int)(Math.floor(Math.abs(Math.random()-0.01f)*questionsAmt)));
        }
        questions.add(new QuestionInstance(this, randomQ, event));
    }

    public void finaliseQuestion(QuestionInstance q){
        questions.remove(q);
        q = null;
    }
}
