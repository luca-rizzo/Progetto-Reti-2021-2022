package winsome.RESTfulUtility;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Scanner;

public class HttpRequest {
    //enumerazione che identifica tutti i metodi possibili nelle richieste verso il server
    public enum Method{
        GET("GET"),
        POST("POST"),
        DELETE("DELETE");

        private final String label;
        Method(String label) {
            this.label = label;
        }
        public String getLabel() {
            return label;
        }
    }

    //contiene tutti gli header della risposta
    private final HashMap<String,String> headers;
    //gestisco solo contenuti JSON: il corpo sarà una stringa in JSON
    private String body;
    //versione HTTP
    private String version = "HTTP/1.1";
    //metodo associato alla richiesta
    private String method;
    //identificativo risorsa
    private String URL;

    private HttpRequest(){
        headers = new HashMap<>();
        body = null;
    }

    //effects: ritorna un oggetto HttpRequest con la riga di richiesta settata sulla base dell'URL e method passato come argomento
    public static HttpRequest newRequest(String URL, Method method){
        HttpRequest request = new HttpRequest();
        request.method = method.getLabel();
        request.URL = URL;
        return request;
    }

    //effects: parsa la stringa plainText passata come argomento e genera l'oggetto HttpRequest associato.
    //solleva una ParseException in caso di errori nel parsing (se la stringa non rispetta sintassi Http)
    static public HttpRequest newHttpRequestFromString (String plainText) throws ParseException {
        if(plainText == null){
            throw new ParseException("Null string", 0);
        }
        HttpRequest request = new HttpRequest();
        Scanner scanner = new Scanner(plainText);
        String line;
        line = scanner.nextLine();
        String[] tokens = line.split(" ");
        //request line ha 3 token
        if(tokens.length!=3)
            throw new ParseException(plainText, 0);
        request.method = tokens[0];
        request.URL = tokens[1];
        request.version = tokens[2];
        while (!(line = scanner.nextLine()).isEmpty()){ //devo giungere alla linea di separazione dal "possibile body"
            tokens = line.replaceFirst(": ", " ").split(" ",2);
            //ogni header ha 2 token
            if(tokens.length!=2)
                throw new ParseException(line, 0);
            request.headers.put(tokens[0], tokens[1]);
        }
        if(request.headers.containsKey("Content-Length") && !request.headers.get("Content-Length").equals("0")){
            //c'è un body
            line = scanner.nextLine();
            String bodyText = plainText.substring(plainText.indexOf(line));
            //controllo che il contenuto del body sia di dimensione uguale a quella dichiarata nel campo Content-length
            if(bodyText.length() != Integer.parseInt(request.headers.get("Content-Length")))
                throw new ParseException(bodyText,0);
            request.body = bodyText;
        }
        return request;
    }

    //effects: aggunge un header alla richiesta Http
    public void addHeader(String headerName, String value){
        headers.put(headerName,value);
    }

    //effects: aggiunge un body alla risposta Http. Verrà settato automaticamente il campo Content-Length e Content-Type (trattiamo solo corpi testuali formato JSON)
    public void setBody(String body) {
        this.body = body;
        this.addHeader("Content-Length", "" + body.length());
        this.addHeader("Content-Type", "application/json");
    }

    //effects: ritorna il ByteBuffer associato all'oggetto che rappresenta una richiesta Http
    public ByteBuffer getRelateByteBuffer(){
        StringBuilder content = new StringBuilder();
        content.append(method + " " + URL + " " + version + "\r\n");
        for (HashMap.Entry<String, String> entry : headers.entrySet()) {
            content.append(entry.getKey() + ": " + entry.getValue() + "\r\n");
        }
        content.append("\r\n");
        if(body!=null){
            content.append(body);
        }
        return ByteBuffer.wrap(content.toString().getBytes(StandardCharsets.UTF_8));
    }

    //*****GETTER AND SETTER*****//
    public String getHeaderValue(String headerName){
        return headers.get(headerName);
    }

    public String getMethod() {
        return method;
    }

    public String getURL() {
        return URL;
    }

    public String getVersion() {
        return version;
    }
    public void setVersion(String version) {
        this.version = version;
    }

    public String getBody() {
        return body;
    }
}
