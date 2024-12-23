import java.io.*;
import java.net.*;
import java.util.*;

public class Serveur {

    private static String ipServeur;
    private static int portServeur;
    private static String dossierDestination;
    
   
    private static  String[] SOUS_SERVEURS ;
   
    
    private static  int[] PORTS_SOUS_SERVEURS ;
  

    private ServerSocket serverSocket;
    //charger configuration serveur / sous serveur
public static void loadConfig() {
    try (BufferedReader reader = new BufferedReader(new FileReader("server_config.txt"))) {
        String line;
        List<String> sousServeursIps = new ArrayList<>();
        List<Integer> sousServeursPorts = new ArrayList<>();

        while ((line = reader.readLine()) != null) {
        
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }

     
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();

                switch (key) {
                    case "ipServeur":
                        ipServeur = value;
                        break;
                    case "portServeur":
                        portServeur = Integer.parseInt(value);
                        break;
                    case "dossierDestination":
                        dossierDestination = value;
                        break;
                    // Gestion des sous-serveurs
                    case "sousServeur1_ip":
                        sousServeursIps.add(value);
                        break;
                    case "sousServeur1_port":
                        sousServeursPorts.add(Integer.parseInt(value));
                        break;
                    case "sousServeur2_ip":
                        sousServeursIps.add(value);
                        break;
                    case "sousServeur2_port":
                        sousServeursPorts.add(Integer.parseInt(value));
                        break;
                    case "sousServeur3_ip":
                        sousServeursIps.add(value);
                        break;
                    case "sousServeur3_port":
                        sousServeursPorts.add(Integer.parseInt(value));
                        break;
               
                    default:
                        System.out.println("Clé inconnue dans la configuration : " + key);
                        break;
                }
            }
        }

        // Vérification de la configuration
        if (sousServeursIps.isEmpty() || sousServeursPorts.isEmpty()) {
            System.out.println("Aucun sous-serveur configuré. Vérifiez votre fichier de configuration.");
            System.exit(1); 
        }

        // Convertir les listes en tableaux pour les sous-serveurs
        SOUS_SERVEURS = sousServeursIps.toArray(new String[0]);
        PORTS_SOUS_SERVEURS = sousServeursPorts.stream().mapToInt(i -> i).toArray();
    } catch (IOException e) {
        e.printStackTrace();
    }
}

    // Constructeur du serveur principal
    public Serveur() {
        try {
            serverSocket = new ServerSocket(portServeur, 50, InetAddress.getByName(ipServeur));
            System.out.println("Serveur principal démarré sur " + ipServeur + ":" + portServeur);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    //demmarage sous serveur
     private static void startSousServeur() {
 Thread sousServeurThread1 = new Thread(new SousServeur1());
Thread sousServeurThread2 = new Thread(new SousServeur2());
 Thread sousServeurThread3 = new Thread(new SousServeur3());

sousServeurThread1.start();
sousServeurThread2.start();
sousServeurThread3.start();

    } 
//decoupage fichier par sous serveur
private static void splitAndDistributeFile(File file) throws IOException {
    // Créer le répertoire temporaire si nécessaire
    File tempDir = new File("temp");
    if (!tempDir.exists()) {
        if (!tempDir.mkdir()) {
            throw new IOException("Impossible de créer le répertoire 'temp'.");
        }
    }

    final int numParts =   SOUS_SERVEURS.length; // Exemple de découpage en 2 parties

    long fileSize = file.length();
    long partSize = fileSize / numParts;
    long remainingBytes = fileSize % numParts;

    try (FileInputStream fileIn = new FileInputStream(file)) {
        for (int i = 0; i < numParts; i++) {
            File partFile = new File(tempDir, file.getName() + ".part" + (i + 1));

            try (FileOutputStream partOut = new FileOutputStream(partFile)) {
                byte[] buffer = new byte[4096];
                long bytesToWrite = partSize + (i == numParts - 1 ? remainingBytes : 0);
                int bytesRead;

                while (bytesToWrite > 0 && (bytesRead = fileIn.read(buffer, 0, (int) Math.min(buffer.length, bytesToWrite))) > 0) {
                    partOut.write(buffer, 0, bytesRead);
                    bytesToWrite -= bytesRead;
                }
            }

            // Envoi de la partie au sous-serveur
            distributePartToSubServer(partFile, SOUS_SERVEURS[i], PORTS_SOUS_SERVEURS[i]);  

            // Supprimer le fichier temporaire après l'envoi
            if (!partFile.delete()) {
                System.out.println("Erreur lors de la suppression du fichier temporaire : " + partFile.getName());
            }
        }
    }
}

  
 //envoi partie de fichier a chaque sous serveur 
private static void distributePartToSubServer(File partFile, String ipServeur, int port) {
    try (Socket socket = new Socket(ipServeur, port);  
         OutputStream out = socket.getOutputStream();
         DataOutputStream dataOut = new DataOutputStream(out);
         FileInputStream fileIn = new FileInputStream(partFile)) {

        // Envoyer la commande "SEND" au sous-serveur
        dataOut.writeUTF("SEND");
        dataOut.writeUTF(partFile.getName());
        dataOut.writeLong(partFile.length());

        // Transférer la partie du fichier au sous-serveur
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fileIn.read(buffer)) > 0) {
            dataOut.write(buffer, 0, bytesRead);
        }
        dataOut.flush(); 

        System.out.println("Partie " + partFile.getName() + " envoyée à " + ipServeur + ":" + port);
 
    } catch (IOException e) {
        System.err.println("Erreur lors de l'envoi de la partie " + partFile.getName() + " au sous-serveur " + ipServeur + ":" + port);
        e.printStackTrace();
    }
}
//recuperer les parties  au sous serveur
private void getFilePartsFromSubServers(Socket clientSocket, String fileName) throws IOException {
    try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
         DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

        boolean recu = true; //pour verifier si tous les parties existes
   
        String partFileName1 = fileName + ".part1";
        String partFileName2 = fileName + ".part2";
          String partFileName3 = fileName + ".part3";

       
        List<byte[]> filePartsData = new ArrayList<>();

        for (int i = 0; i < SOUS_SERVEURS.length; i++) {
            String sousServeurIp = SOUS_SERVEURS[i];
            int sousServeurPort = PORTS_SOUS_SERVEURS[i];

           
            try (Socket sousServeurSocket = new Socket(sousServeurIp, sousServeurPort);
                 DataInputStream sousDis = new DataInputStream(sousServeurSocket.getInputStream());
                 DataOutputStream sousDos = new DataOutputStream(sousServeurSocket.getOutputStream())) {

                // Envoyer la commande GET au sous-serveur
                sousDos.writeUTF("GET");
                 String partFileName = (i == 0 ? partFileName1 : (i == 1 ? partFileName2 : partFileName3));
            sousDos.writeUTF(partFileName);

                sousDos.flush();

               
                long partSize = sousDis.readLong();
                if (partSize == 0) {
                    System.out.println("La partie du fichier n'existe pas sur le sous-serveur : " + (i == 0 ? partFileName1 : partFileName2));
                    recu = false;  
                    continue; 
                }

              
                byte[] filePartData = new byte[(int) partSize];
                int bytesRead = 0;
                while (bytesRead < partSize) {
                    int read = sousDis.read(filePartData, bytesRead, filePartData.length - bytesRead);
                    if (read == -1) {
                        throw new EOFException("Fin de flux atteinte avant la fin des données.");
                    }
                    bytesRead += read;
                }

             
                filePartsData.add(filePartData);

            
                System.out.println("Partie du fichier " + (i == 0 ? partFileName1 : partFileName2) + " reçue avec succès.");
            } catch (IOException e) {
                System.err.println("Erreur lors de la récupération de la partie du fichier : " + e.getMessage());
            }
        }

   
        if (recu) {
        
            File assembledFile = assembleFileLocally(filePartsData, fileName);

            try {
                if (assembledFile != null) {
                  
                    sendFile(clientSocket, assembledFile);
                } else {
                    System.out.println("Le fichier assemblé est null.");
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de l'envoi du fichier assemblé : " + e.getMessage());
            }

         
            if (assembledFile != null && assembledFile.delete()) {
                System.out.println("Le fichier assemblé a été supprimé avec succès.");
            } else {
                System.out.println("Erreur lors de la suppression du fichier assemblé.");
            }
        } else {
            System.out.println("Une ou plusieurs parties du fichier sont manquantes.");
        }

    } catch (IOException e) {
        System.err.println("Erreur lors de la récupération des parties du fichier : " + e.getMessage());
    }
}

//assemblage des parties pour avoir le fichier
private File assembleFileLocally(List<byte[]> filePartsData, String fileName) throws IOException {

    File assembledFile = new File(dossierDestination, fileName); 

    try (FileOutputStream fos = new FileOutputStream(assembledFile)) {
      
        for (byte[] partData : filePartsData) {
            fos.write(partData);
        }
    }

    
    System.out.println("Le fichier " + fileName + " a été assemblé et stocké à : " + assembledFile.getAbsolutePath());

    return assembledFile; // Retourner le fichier assemblé
}
//renvoyer le fichier
 private void sendFile(Socket clientSocket, File file) throws Exception {

    if (!file.exists()) {
        throw new Exception("Le fichier " + file.getName() + " n'existe pas sur le serveur.");
    }


    if (file.isDirectory()) {
        throw new Exception("Le chemin spécifié est un répertoire, pas un fichier : " + file.getAbsolutePath());
    }

    try (DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
         FileInputStream fileInputStream = new FileInputStream(file)) {


        dataOutputStream.writeUTF("OK");

    
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            dataOutputStream.write(buffer, 0, bytesRead);
        }
        System.out.println("Fichier envoyé avec succès : " + file.getAbsolutePath());
    } catch (IOException e) {
        e.printStackTrace();
        throw new Exception("Une erreur s'est produite lors de l'envoi du fichier : " + file.getName());
    }
}   


//obtenir un fichier   
private File getFichier(Socket clientSocket, String fileName) {
    File receivedFile = null;
    try (DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
         DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream())) {

        
        receivedFile = new File(dossierDestination + File.separator + fileName);

     
        if (fileName.isEmpty() || fileName.contains("..")) {
            dataOutputStream.writeUTF("Erreur : Nom de fichier invalide");
            return null; 
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream(receivedFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;

      
            while ((bytesRead = dataInputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }

           
            fileOutputStream.flush();
            System.out.println("Fichier reçu avec succès : " + fileName);

         
            dataOutputStream.writeUTF("Fichier reçu avec succès : " + fileName);

        } catch (EOFException e) {
            System.err.println("Connexion perdue ou déconnexion prématurée. Fichier non complet : " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
       
            dataOutputStream.writeUTF("Erreur lors de la réception du fichier : " + fileName);
        }

    } catch (IOException e) {
        e.printStackTrace();
 
        try (DataOutputStream errorStream = new DataOutputStream(clientSocket.getOutputStream())) {
            errorStream.writeUTF("Erreur lors de la réception du fichier : " + fileName);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }


    return receivedFile;
}
//gerer le client
private void handleClient(Socket clientSocket) throws IOException {
   
    try (DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream());
         DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream())) {

        
        String command = dataInputStream.readUTF();
        System.out.println("Commande reçue : " + command);


        if ("SEND".equals(command)) {
      
            String fileName = dataInputStream.readUTF();
            System.out.println("Nom du fichier à recevoir : " + fileName);

            
            File file = getFichier(clientSocket, fileName);

          
            if (file != null) {
               
                try {
                  
                    splitAndDistributeFile(file);
                    file.delete();
                    dataOutputStream.writeUTF("Fichier reçu et distribué avec succès.");
                } catch (IOException e) {
                
                    dataOutputStream.writeUTF("Erreur lors du découpage ou de l'envoi du fichier.");
                    System.out.println("error");
                  
                }
            } else {
             
                dataOutputStream.writeUTF("Erreur : Le fichier n'a pas été reçu correctement.");
            }
        }
        
        else if ("GET".equals(command)) {
            
            String fileName = dataInputStream.readUTF();
            System.out.println("Client demande le fichier : " + fileName);

           
            getFilePartsFromSubServers(clientSocket, fileName);
        } else {
       
            dataOutputStream.writeUTF("Commande inconnue.");
        }
    } catch (IOException e) {
      
       
    }
}
  // Méthode pour démarrer le serveur et accepter les connexions
    public void demarrer() {
        startSousServeur();
        try {
            while (true) {
              
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connecté depuis " + clientSocket.getInetAddress().getHostAddress());

                // Gérer le client dans un thread séparé
                new Thread(() -> {
                    try {
                        handleClient(clientSocket);
                    } catch (IOException e) {
                       
                    }
                }).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
   

    public static void main(String[] args) {
        // Charger la configuration depuis le fichier
        loadConfig();

     
            // Démarrer le serveur principal
            Serveur serveur = new Serveur();
            serveur.demarrer();
       
    }
}
