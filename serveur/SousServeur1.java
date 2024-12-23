import java.io.*;
import java.net.*;
import java.util.*;

public class SousServeur1 extends Thread {
  
    private static String ipSousServeur;
    private static int portSousServeur;
    private static String dossierSousServeur; // Répertoire spécifique à ce sous-serveur

   
    public SousServeur1() {
        loadConfig(); 
    }

    // Méthode pour charger la configuration depuis un fichier
    private static void loadConfig() {
        Properties properties = new Properties();
        
        try (FileInputStream fis = new FileInputStream("server_config.txt")) {
            properties.load(fis);
            
      
            ipSousServeur = properties.getProperty("sousServeur1_ip", "127.0.0.1");
            portSousServeur = Integer.parseInt(properties.getProperty("sousServeur1_port", "5001"));
            dossierSousServeur = properties.getProperty("dossierDestination1", "./server_files/s1");

        
            System.out.println("Configuration du sous-serveur chargée :");
            System.out.println("IP: " + ipSousServeur);
            System.out.println("Port: " + portSousServeur);
            System.out.println("Dossier: " + dossierSousServeur);
        } catch (IOException e) {
            System.out.println("Erreur lors du chargement de la configuration : " + e.getMessage());
            e.printStackTrace();
            
        
            ipSousServeur = "127.0.0.1";
            portSousServeur = 5001;
            dossierSousServeur = "./server_files/s1";
            System.out.println("Utilisation des valeurs par défaut :");
            System.out.println("IP: " + ipSousServeur);
            System.out.println("Port: " + portSousServeur);
            System.out.println("Dossier: " + dossierSousServeur);
        }
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(portSousServeur, 50, InetAddress.getByName(ipSousServeur))) {
            System.out.println("Sous-serveur démarré, en attente de connexions...");
            while (true) {
           
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> {
                    try {
                        handleServerCommand(clientSocket); 
                    } catch (IOException e) {
                        System.out.println("Erreur lors du traitement de la commande : " + e.getMessage());
                        e.printStackTrace();
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("Erreur de serveur : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Fonction pour traiter les commandes envoyées par le serveur
private void handleServerCommand(Socket clientSocket) throws IOException {
    try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
         DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

    
        String command = dis.readUTF();
        System.out.println("Sous-serveur : Commande reçue : " + command);

        switch (command) {
            case "SEND":
            
                receiveAndSaveFilePart(clientSocket);
                  break;

            case "GET":
        
                System.out.println("Sous-serveur : Commande 'GET' reçue. Début de l'envoi de la partie du fichier.");

          
                String partFileName = dis.readUTF();

              
                byte[] filePartData = getFilePart(partFileName);

                if (filePartData != null) {
                 dos.writeLong(filePartData.length); 

                    dos.write(filePartData);
                    dos.flush();
                    System.out.println("Partie du fichier envoyée au serveur principal : " + partFileName);
                } else {
                 
                    dos.writeLong(0);
                    dos.flush(); 
                    System.out.println("La partie du fichier n'a pas été trouvée : " + partFileName);
                }
               break;

            case "end":
                System.out.println("Sous-serveur : Commande 'end' reçue. Fin de la réception.");
                break;

            default:
                System.out.println("Sous-serveur : Commande inconnue reçue : " + command);
                break;
        }
    }
}



    // Fonction de réception et de sauvegarde d'une partie du fichier
private void receiveAndSaveFilePart(Socket clientSocket) throws IOException {
    try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream())) {
    
        String fileName = dis.readUTF();
        System.out.println("Réception du fichier : " + fileName);

     
        long fileSize = dis.readLong();
        System.out.println("Taille du fichier : " + fileSize + " octets");

       File serverDirectory = new File(dossierSousServeur);
        
 
        if (!serverDirectory.exists()) {
            if (!serverDirectory.mkdirs()) {
                throw new IOException("Impossible de créer le répertoire : " + dossierSousServeur);
            }
            System.out.println("Dossier créé : " + dossierSousServeur);
        }


        File receivedFile = new File(serverDirectory, fileName);

       
        if (receivedFile.exists()) {
            System.out.println("Le fichier existe déjà, il sera remplacé.");
            receivedFile.delete();  
        }

        try (FileOutputStream fileOut = new FileOutputStream(receivedFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long bytesReceived = 0;

       
            while ((bytesRead = dis.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                bytesReceived += bytesRead;

            
                if (bytesReceived % 10240 == 0) {
                    System.out.println("Réception de " + bytesReceived + " octets...");
                }
            }

       
            if (bytesReceived == fileSize) {
                System.out.println("Fichier reçu avec succès : " + receivedFile.getAbsolutePath());
            } else {
                System.out.println("Erreur : Le fichier reçu est incomplet. Taille attendue : " + fileSize + ", Taille reçue : " + bytesReceived);
              
                receivedFile.delete();
            }
        }

       
        try (DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
            dos.writeUTF("Fichier reçu et traité");
            dos.flush();
        }

    } catch (IOException e) {
        System.out.println("Erreur lors de la réception du fichier : " + e.getMessage());
        e.printStackTrace();
    }
}

//renvoyer les parties defichier
  private byte[] getFilePart(String partFileName) throws IOException {

    File file = new File(dossierSousServeur, partFileName);

    if (file.exists() && file.isFile()) {
   
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] fileData = new byte[(int) file.length()];
            fis.read(fileData);
            return fileData;
        }
    }
    

    return null;
}


    public static void main(String[] args) {
        SousServeur1 sousServeur1 = new SousServeur1(); 
        sousServeur1.start(); 
    }
}
