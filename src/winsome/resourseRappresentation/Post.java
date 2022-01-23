package winsome.resourseRappresentation;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Post {
    //titolo del post
    final private String titolo;
    //contenuto del post
    final private String contenuto;
    //riferimento all'autore del post
    final private User author;
    //counter per la creazione di idPodt univoci
    static private final AtomicLong count = new AtomicLong(0);
    //id univoco associato al post
    final private long id;
    //set di reaction associate al post
    final private RWHashSet<Reaction> reaction;
    //lock per accedere alle variabili di stato non final
    final private ReentrantReadWriteLock stateRWlock;
    //indica il numero di iterazione del processo di calcolo a cui è stato sottoposto
    private long iterazioni;


    public Post(User author, String titolo, String contenuto) {
        this.author = author;
        this.titolo = titolo;
        this.contenuto = contenuto;
        this.reaction = new RWHashSet<>();
        this.id = count.incrementAndGet();
        this.iterazioni = 0L;
        this.stateRWlock = new ReentrantReadWriteLock();
    }
    //costruttore usato per ripristinare lo stato del winsome.server
    public Post(User author, String titolo, String contenuto, long id, long iterazioni) {
        this.author = author;
        this.titolo = titolo;
        this.contenuto = contenuto;
        this.reaction = new RWHashSet<>();
        this.id = id;
        this.iterazioni = iterazioni;
        //dobbiamo sempre garantire univocità di idPost
        count.set(Math.max(count.incrementAndGet(),id));
        this.stateRWlock = new ReentrantReadWriteLock();
    }

    //GETTERS AND SETTERS
    public String getTitolo() {
        return titolo;
    }

    public String getContenuto() {
        return contenuto;
    }

    public long getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }


    public RWHashSet<Reaction> getReaction() {
        return reaction;
    }

    public long getIterazioni() {
        try{
            //iterazioni non è final e viene acceduta(possibilmente) dal thread rewardCalculator e DataBackup contemporaneamente-->devo gestire concorrenza
            stateRWlock.readLock().lock();
            return iterazioni;
        }finally{
            stateRWlock.readLock().unlock();
        }
    }

    public long incrementAndGetIterazioni(){
        try{
            //iterazioni non è final e viene acceduta(possibilmente) dal thread rewardCalculator e DataBackup contemporaneamente-->devo gestire concorrenza
            stateRWlock.writeLock().lock();
            this.iterazioni += 1;
            return iterazioni;
        }finally{
            stateRWlock.writeLock().unlock();
        }
    }
}
