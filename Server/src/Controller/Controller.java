package Controller;

import Model.CFile;
import Model.Folder;
import Services.ServerService;
import Services.StreamingService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Function;

public class Controller {

    private String currentPath;
    private int portNum;
    private ServerService serverService;
    private Folder root;

    public Controller(){
        currentPath = "";
    }


    public void processFileChooserInput(String path){
        currentPath = path;
        root = new Folder(new File(path));
    }

    public void startServerService(int portNum, Function<Boolean, Void> callback){
        if (!currentPath.equals("")){ // ensure a folder is actually selected
            this.portNum = portNum;
            serverService = new ServerService(portNum);
            serverService.startServer(this::createContexts);
            callback.apply(true);
        }
    }

    private Void refresh(HttpServer server){
        removeContexts(root, server);
        System.out.println("removed contexts");
        processFileChooserInput(root.getFile().getPath());
        createContexts(server);
        System.out.println("Readded contexts");
        return null;
    }

    private void removeContexts(Folder folder, HttpServer server){

        server.removeContext(folder.getPathFromRoot());

        for (CFile file: folder.getFiles()){
            server.removeContext(file.getPathFromRoot());
        }

        for (Folder subFolder: folder.getFolders()){
            removeContexts(subFolder, server);
        }
    }


    public void stopServerService(Function<Boolean, Void> callback){
        serverService.exit();
        serverService = null;
        callback.apply(false);
    }

    private Void createContexts(HttpServer server){
        createContexts(server, root);
        server.createContext("/Refresh/", new RefreshHandler(server, this::refresh));
        return null;
    }

    private void createContexts(HttpServer server, Folder folder){
        server.createContext(folder.getPathFromRoot(), new MyHandler(folder));
        
        for (CFile file: folder.getFiles()){
            server.createContext(file.getPathFromRoot(), new FileHandler(file, portNum));
        }

        for (Folder subFolder: folder.getFolders()){
           createContexts(server, subFolder);
        }
    }

    static class FileHandler implements HttpHandler{

        private final CFile file;
        private final int portNum;
        public FileHandler(CFile file, int portNum){
            this.file = file;
            this.portNum = portNum;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    JSONObject response = new JSONObject();
                    response.put("file", file.getName());
                    t.sendResponseHeaders(200, response.toString().getBytes().length);

                    StreamingService streamingService = new StreamingService(portNum + 1, file);

                    OutputStream os = t.getResponseBody();
                    os.write(response.toString().getBytes());
                    os.close();
                    return null;
                }
            };
            worker.execute();
        }
    }

    static class MyHandler implements HttpHandler {
        private final Folder folder;

        public MyHandler(Folder folder){
            this.folder = folder;
        }

         @Override
        public void handle(HttpExchange t) throws IOException {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    JSONObject response = new JSONObject();
                    response.put("message", "a message");
                    response.put("currentFolder", folder.getFile().getName());
                    response.put("path", folder.getPathFromRoot());
                    response.put("folders", folder.getJSONTopLevelFolders());
                    response.put("files", folder.getJSONFiles());
                    System.out.println(response.toString());


                    t.sendResponseHeaders(200, response.toString().getBytes().length);
                    OutputStream os = t.getResponseBody();
                    os.write(response.toString().getBytes());
                    os.close();
                    return null;
                }
            };
           worker.execute();
        }
    }

    static class RefreshHandler implements HttpHandler {
        private HttpServer server;

        private Function<HttpServer, Void> refreshCallback;


        public RefreshHandler(HttpServer server, Function<HttpServer, Void> refreshCallback){
            this.server = server;
            this.refreshCallback = refreshCallback;
        }

        @Override
        public void handle(HttpExchange t) throws IOException {
            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    JSONObject response = new JSONObject();
                    response.put("message", "Refreshed!");
                    System.out.println(response.toString());

                    refreshCallback.apply(server);

                    t.sendResponseHeaders(200, response.toString().getBytes().length);
                    OutputStream os = t.getResponseBody();
                    os.write(response.toString().getBytes());
                    os.close();
                    return null;
                }
            };
            worker.execute();
        }
    }
}
