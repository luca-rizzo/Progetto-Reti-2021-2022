package winsome.RESTfulUtility;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class Token {

    //counter atomico usato per assegnare id univoci ai Token
    final static private AtomicLong count = new AtomicLong(0);
    //id univoco associato al token
    final private long id;
    //username dell'utente a cui tale token è associato
    final private String usernameAssociated;
    //apiKey per l'accesso alle risorse dell'utente usernameAssociated
    private String apiKey;

    public Token(String usernameAssociated) {
        this.id = count.incrementAndGet();
        this.apiKey = generateString() + id;
        this.usernameAssociated = usernameAssociated;
    }

    //costruttore usato per creare fake token ed eseguire ricerche nella ConcurrentHashMap loggedUsers: evita di incrementare il valore "count"
    //per i token "di ricerca" nella ConcurrentHashMap
    public Token(){
        this.id = -1;
        this.usernameAssociated = "";
    }
    //costruttore usato per creare fake token ed eseguire ricerche nella ConcurrentHashMap loggedUsers: evita di incrementare il valore "count"
    //per i token "di ricerca" nella ConcurrentHashMap
    public static Token createFakeToken(String apiKey){
        Token token = new Token();
        token.setApiKey(apiKey);
        return token;
    }

    //metodo equals riscritto per effettuare in maniera atomica putIfAbsent sulla struttura dati loggedUsers
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        //due token "sono uguali"/"collidono"/"entrano in contrasto"/"non possono esistere contemporaneamente" <-> hanno lo stesso apiKey o sono associati allo stesso utente
        Token token = (Token) o;
        return (apiKey.equals(token.apiKey) || usernameAssociated.equals(token.usernameAssociated));
    }
    @Override
    public int hashCode() {
        return 0;
    }

    //effects: ritorna una stringa casuale
    private String generateString() {
        return UUID.randomUUID().toString();
    }

    //*****GETTER AND SETTER*****//
    public long getId() {
        return id;
    }
    public String getApiKey() {
        return apiKey;
    }
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
