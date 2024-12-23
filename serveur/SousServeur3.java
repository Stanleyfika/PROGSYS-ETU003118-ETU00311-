import java.io.*;
import java.net.*;
import java.util.*;

public class SousServeur3 extends Thread {
    // Variables statiques pour les configurations
    private static String ipSousServeur;
    private static int portSousServeur;
    private static String dossierSousServeur; // Répertoire spécifique à ce sous-serveur

    // Constructeur pour initialiser le sous-serveur
    public SousServeur3() {
        loadConfig(); // Charge la configuration lors de la création de l'objet
    }

    // Méthode pour charger la configuration depuis un fichier
    private static void loadConfig() {
        Properties properties = new Properties();
        
        try (FileInputStream fis = new FileInputStream("server_config.txt")) {
            properties.load(fis);
            
            // Charger les paramètres du sous-serveur
            ipSousServeur = properties.getProperty("sousServeur3_ip", "127.0.0.1");
            portSousServeur = Integer.parseInt(properties.getProperty("sousServeur3_port", "5001"));
            dossierSousServeur = properties.getProperty("dossierDestination3", "./server_files/s1");

            // Afficher les valeurs chargées
            System.out.println("Configuration du sous-serveur chargée :");
            System.out.println("IP: " + ipSousServeur);
            System.out.println("Port: " + portSousServeur);
            System.out.println("Dossier: " + dossierSousServeur);
        } catch (IOException e) {
            System.out.println("Erreur lors du chargement de la configuration : " + e.getMessage());
            e.printStackTrace();
            
            // Valeurs par défaut en cas d'erreur de chargement
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
                // Attente d'une connexion du serveur principal
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> {
                    try {
                        handleServerCommand(clientSocket); // Gérer la commande reçue
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

    // Fonction pour traiter les commandes envoyées par le serveur principal
private void handleServerCommand(Socket clientSocket) throws IOException {
    try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
         DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {

        // Lire la commande envoyée par le serveur principal
        String command = dis.readUTF();
        System.out.println("Sous-serveur : Commande reçue : " + command);

        switch (command) {
            case "SEND":
                // Gérer la réception et l'enregistrement de la partie du fichier
                receiveAndSaveFilePart(clientSocket);
       break;

            case "GET":
                // Commande GET reçue, demander la partie du fichier
                System.out.println("Sous-serveur : Commande 'GET' reçue. Début de l'envoi de la partie du fichier.");

                // Lire le nom de la partie du fichier demandée
                String partFileName = dis.readUTF();

                // Appeler la méthode pour récupérer les données de la partie du fichier
                byte[] filePartData = getFilePart(partFileName);

                if (filePartData != null) {
                    // Si la partie du fichier existe, envoyer sa taille puis les données
                    dos.writeLong(filePartData.length); // Envoyer la taille de la partie du fichier
                    dos.flush();

                    dos.write(filePartData); // Envoyer la partie du fichier
                    dos.flush();
                    System.out.println("Partie du fichier envoyée au serveur principal : " + partFileName);
                } else {
                    // Si la partie du fichier n'existe pas, envoyer une taille de 0
                    dos.writeLong(0);
                    dos.flush(); // Assurez-vous que la taille est envoyée avant les données
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
        // Lire le nom du fichier envoyé par le client
        String fileName = dis.readUTF();
        System.out.println("Réception du fichier : " + fileName);

        // Lire la taille du fichier
        long fileSize = dis.readLong();
        System.out.println("Taille du fichier : " + fileSize + " octets");


         File serverDirectory = new File(dossierSousServeur);
        
        // Vérifier si le dossier existe, sinon le créer
        if (!serverDirectory.exists()) {
            if (!serverDirectory.mkdirs()) {
                throw new IOException("Impossible de créer le répertoire : " + dossierSousServeur);
            }
            System.out.println("Dossier créé : " + dossierSousServeur);
        }

        // Créer un fichier dans ce répertoire
        File receivedFile = new File(serverDirectory, fileName);

     

        // Vérifier si le fichier existe déjà, et le supprimer si nécessaire
        if (receivedFile.exists()) {
            System.out.println("Le fichier existe déjà, il sera remplacé.");
            receivedFile.delete();  // Optionnel, à condition que vous vouliez écraser les fichiers existants
        }

        try (FileOutputStream fileOut = new FileOutputStream(receivedFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            long bytesReceived = 0;

            // Lire les données et les écrire dans le fichier
            while ((bytesRead = dis.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                bytesReceived += bytesRead;

                // Vérification périodique du nombre d'octets reçus
                if (bytesReceived % 10240 == 0) {
                    System.out.println("Réception de " + bytesReceived + " octets...");
                }
            }

            // Vérification si le fichier est complet
            if (bytesReceived == fileSize) {
                System.out.println("Fichier reçu avec succès : " + receivedFile.getAbsolutePath());
            } else {
                System.out.println("Erreur : Le fichier reçu est incomplet. Taille attendue : " + fileSize + ", Taille reçue : " + bytesReceived);
                // Vous pouvez décider de supprimer le fichier partiellement reçu si nécessaire
                receivedFile.delete();
            }
        }

        // Envoi de la confirmation seulement après avoir terminé la réception du fichier
        try (DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
            dos.writeUTF("Fichier reçu et traité");
            dos.flush();
        }

    } catch (IOException e) {
        System.out.println("Erreur lors de la réception du fichier : " + e.getMessage());
        e.printStackTrace();
    }
}


    private byte[] getFilePart(String partFileName) throws IOException {
        File file = new File(dossierSousServeur, partFileName);
        if (file.exists() && file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] fileData = new byte[(int) file.length()];
                fis.read(fileData);
                return fileData;
            }
        }
        return null;  // Si la partie du fichier n'existe pas
    }

    // Point d'entrée pour démarrer le sous-serveur
    public static void main(String[] args) {
        SousServeur3 sousServeur = new SousServeur3(); // Création de l'instance du sous-serveur
        sousServeur.start(); // Démarre le thread pour gérer les connexions entrantes
    }
}
