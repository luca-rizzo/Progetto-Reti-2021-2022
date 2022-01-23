package winsome.resourseRappresentation;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Reaction {
    //indica il tipo di reaction: 0 per rate, 1 per commento
    final private int type;
    //indica il contenuto della reaction: null per like
    final private String content;
    //indica il voto della reaction: 0 per commento; +1,-1 per rate
    final private int rate;
    //mantiene un riferimento all'autore della reaction
    final private User author;

    //lock per accedere alle variabili di stato non final
    private final ReentrantReadWriteLock stateRWlock;
    //variabile è stata o meno conteggiata dal processo di calcolo dei reward
    private Boolean isNew;

    private Reaction(int type, String content, int rate, User author, Boolean isNew) {
        if(author == null){
            throw new IllegalArgumentException();
        }
        this.type = type;
        this.content = content;
        this.rate = rate;
        this.author = author;
        this.isNew = isNew;
        this.stateRWlock = new ReentrantReadWriteLock();
    }

    //effects: ritorna un nuovo commento
    public static Reaction newComment(User author, String content, boolean isNew){
        if(content == null || author == null){
            throw new IllegalArgumentException();
        }
        return new Reaction(1,content,0, author, isNew);
    }

    //effects: ritorna una reaction che rappresenta un voto
    public static Reaction newVote(User author, int rate, boolean isNew){
        if(!(rate == -1  ||  rate == 1) || author == null){
            //rate != -1 o da 1 o user == null
            throw new IllegalArgumentException();
        }
        return new Reaction(0,null, rate, author, isNew);
    }
    //*****GETTERS AND SETTERS*****//
    public int getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public int getRate() {
        return rate;
    }

    public User getAuthor() {
        return author;
    }

    public boolean isNew() {
        //isNew non è final e viene acceduta(possibilmente) dal thread rewardCalculator e DataBackup contemporaneamente-->devo gestire concorrenza
        try{
            stateRWlock.readLock().lock();
            return isNew;
        }finally{
            stateRWlock.readLock().unlock();
        }
    }

    public void setNew(boolean aNew) {
        //isNew non è final e viene acceduta(possibilmente) dal thread rewardCalculator e DataBackup contemporaneamente-->devo gestire concorrenza
        try{
            stateRWlock.writeLock().lock();
            isNew = aNew;
        }finally{
            stateRWlock.writeLock().unlock();
        }
    }

    //metodo equals riscritto per effettuare in maniera atomica add sulla struttura dati reaction associata ad un post
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reaction reaction = (Reaction) o;
        //solo due like sono considerati "uguali"/"collidono
        if (type != reaction.type || type != 0) return false;
        return author.equals(reaction.author);
    }

    @Override
    public int hashCode() {
        int result = type;
        result = 31 * result + author.hashCode();
        return result;
    }
}
