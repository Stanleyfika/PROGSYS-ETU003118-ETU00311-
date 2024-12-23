import java.io.*;
import java.net.*;



public class Client {
    private static String serveurAdresse;
    private static int port;
    public static String DossierDestination;

    public Socket socket;
    private PrintWriter writer;
    private BufferedOutputStream outputStream;
    private BufferedReader reader;

    public Client(String serveurAdresse, int port) {
        this.serveurAdresse = serveurAdresse;
        this.port = port;
    }

    public Client() {}

    // Méthode pour charger la configuration depuis le fichier config.txt
    public static void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader("client_config.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
              
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }

               
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                   
                    switch (key) {
                        case "dossierDestination":
                            DossierDestination = value;
                            break;
                        default:
                            System.out.println("Clé inconnue : " + key);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
//connexion
    public void connect(String adresseServeur) throws IOException {
        String[] adressePort = adresseServeur.split(":");
        if (adressePort.length != 2) {
            throw new IllegalArgumentException("L'adresse doit être sous la forme IP:port");
        }

        this.serveurAdresse = adressePort[0].trim();
        this.port = Integer.parseInt(adressePort[1].trim());

        socket = new Socket(serveurAdresse, port);
        writer = new PrintWriter(socket.getOutputStream(), true);
        outputStream = new BufferedOutputStream(socket.getOutputStream());
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        System.out.println("Connexion réussie au serveur " + serveurAdresse + " sur le port " + port);
    }
//fermeture de connexion
    public void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Connexion fermée.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String[] getListeFichiers() throws IOException {
        try (Socket socket = new Socket(serveurAdresse, port);
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {
    
            // Envoyer la commande LIST au serveur
            dataOutputStream.writeUTF("LIST");
    
        
            int nombreFichiers = dataInputStream.readInt();
            String[] fichiers = new String[nombreFichiers];
    
          
            for (int i = 0; i < nombreFichiers; i++) {
                fichiers[i] = dataInputStream.readUTF();
            }
    
            return fichiers;
        }
    }
        // Méthode pour envoyer un fichier
    public  void sendFile(File file) {
        try (Socket socket = new Socket(serveurAdresse, port);
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             FileInputStream fileInputStream = new FileInputStream(file)) {
    
            // Envoyer la commande "SEND" au serveur
            dataOutputStream.writeUTF("SEND");
    
            // Envoyer le nom du fichier
            dataOutputStream.writeUTF(file.getName());
    
            // Envoyer le fichier par morceaux
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                dataOutputStream.write(buffer, 0, bytesRead);
            }
    
            // Assurer que toutes les données sont envoyées
            dataOutputStream.flush();
    
            System.out.println("Fichier envoyé avec succès : " + file.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //recevoir fichier serveur
 public void getFichier(String fileName) throws IOException {
   
    try (Socket socket = new Socket(serveurAdresse, port);  // Créer un nouveau socket à chaque fois
         DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
         DataInputStream dis = new DataInputStream(socket.getInputStream())) {

        
        File dossierDestination = new File(DossierDestination);

        if (!dossierDestination.exists()) {
            if (!dossierDestination.mkdirs()) {
                throw new IOException("Impossible de créer le répertoire : " + DossierDestination);
            }
        }

    
        File fichierDestination = new File(dossierDestination, fileName);

        // Envoyer la commande GET et le nom du fichier au serveur
        dos.writeUTF("GET");
        dos.writeUTF(fileName);
        dos.flush(); 

        String response = dis.readUTF();

        if ("OK".equals(response)) {
            // Recevoir le fichier
            try (FileOutputStream fos = new FileOutputStream(fichierDestination)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = dis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                System.out.println("Fichier téléchargé avec succès dans : " + fichierDestination.getAbsolutePath());
            }
        } else if (response.startsWith("ERROR")) {
         
            throw new IOException("Erreur côté serveur : " + response);
        }
    } catch (IOException e) {
        System.err.println("Erreur lors de la réception du fichier : " + e.getMessage()+"le fichier n'existe pas dans le serveur");
       
    }
}

//gestion commande
public static void handleCommand(Client client, String command) {
    String[] parts = command.trim().split("\\s+", 2);
    String action = parts[0].toLowerCase();

    try {
        if ("connect".equals(action) && parts.length == 2) {
            client.connect(parts[1]);
            System.out.println("Connecté avec succès à " + parts[1]);
        }else if ("exit".equals(action)) {
            client.closeConnection();
            System.exit(0);
    
        }else if ("get".equals(action) && parts.length == 2) {
           

            String fileName = parts[1];
            try {
                client.getFichier(fileName);  
            } catch (IOException e) {
                System.out.println("Erreur lors de la réception du fichier : " + e.getMessage() + "(il n'existe pas dans le serveur)");
            }
        } else if ("send".equals(action) && parts.length == 2) {
    
            if (client.socket == null || client.socket.isClosed()) {
                System.out.println("Erreur : Vous devez être connecté à un serveur avant d'envoyer un fichier.");
                return;
            }

            File file = new File(parts[1]);
            if (file.exists() && file.isFile()) {
                client.sendFile(file); 
            } else {
                System.out.println("Erreur : Le fichier spécifié n'existe pas.");
            }
        } else {
            System.out.println("Commande inconnue. Utilisez 'connect <ip>:<port>' ou 'exit' pour quitter.");
        }
    } catch (Exception e) {
        System.out.println("Erreur : " + e.getMessage());
    }
}


public static void main(String[] args) {
    loadConfig();


    Client client = new Client();  
    try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
        System.out.println("Client démarré. Tapez 'connect <ip>:<port>' pour vous connecter au serveur.");

        while (true) {
            System.out.print("> ");
            String command = consoleReader.readLine();
            if (command != null && !command.trim().isEmpty()) {
                handleCommand(client, command);  // Passer le même client à chaque commande
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}




}
