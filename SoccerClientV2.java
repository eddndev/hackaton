import java.net.URI;
import java.net.http.*;
import java.util.Scanner;
import com.google.gson.Gson;

class RegisterRequest {
    String team;

    RegisterRequest(String team) {
        this.team = team;
    }
}

class RegisterResponse {
    int playerId;
    String team;
}

class GameStateDTO {
    PlayerDTO you;
    double[] ball;
    PlayerDTO[] players;
}

class PlayerDTO {
    int id;
    String team;
    double x;
    double y;
}

public class SoccerClientV2 {

    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newHttpClient();
        Gson gson = new Gson();

        // Elegir equipo
        String team;
        if (args.length > 0) {
            team = args[0].toUpperCase();
        } else {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Elige tu equipo (A/B): ");
            team = scanner.nextLine().toUpperCase();
        }

        if (!team.equals("A") && !team.equals("B")) {
            System.out.println("Equipo inválido. Se asignará el equipo A por defecto.");
            team = "A";
        }

        // 1. Enviar solicitud de registro
        RegisterRequest registerRequest = new RegisterRequest(team);
        String json = gson.toJson(registerRequest);

        HttpRequest register = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(
                register,
                HttpResponse.BodyHandlers.ofString()
        );

        RegisterResponse registerResponse =
                gson.fromJson(response.body(), RegisterResponse.class);

        int playerId = registerResponse.playerId;
        System.out.println("Registrado como jugador " + playerId +
                           " en el equipo " + registerResponse.team);

        //  Bucle principal del cliente
        while (true) {
            // 2.- Obtener estado
            HttpRequest stateReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/state?playerId=" + playerId))
                    .GET()
                    .build();

            HttpResponse<String> estado = client.send(
                stateReq,
                HttpResponse.BodyHandlers.ofString()
            );

            // Conversión JSON a Java
            GameStateDTO gameState = gson.fromJson(estado.body(), GameStateDTO.class);

            //Obtención de la posición de la pelota
            double ballX = gameState.ball[0];
            double ballY = gameState.ball[1];
            //System.out.println("Posición del balón: (" + ballX + ", " + ballY + ")");
            System.out.printf("Posición del balón: (%.1f, %.1f)%n", ballX, ballY);

            //3. El cliente toma las decisiones de juego, por ejemplo moverse hacia la pelota.

            // Obtengo mi posición
            double playerX = gameState.you.x;
            double playerY = gameState.you.y;
            //System.out.println("Mi posición: (" + playerX + ", " + playerY + ")");
            System.out.printf("Mi posición: (%.1f, %.1f)%n", playerX, playerY);

            // Vector dirección hacia la pelota
            double dx = ballX - playerX;
            double dy = ballY - playerY;

            // Distancia entre mi posición y la pelota
            double dist = Math.sqrt(dx*dx + dy*dy);

            // Normalizamos el vector de dirección para enviarlo en caso de que nos movemos hacia la pelota
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length > 0) {
                dx /= length;
                dy /= length;
            }

            String action;
            // Si estamos sobre la pelota podemos patear
            if (dist < 1) {
                // patear hacia la dirección que se desea.
                if(team.equals("A")){
                    action = "KICK " + playerId + " 1 0 5";
                } else {
                    action = "KICK " + playerId + " -1 0 5";
                }
                
            } else { // Si no nos movemos hacia el balón
                action = "MOVE " + playerId + " " + dx + " " + dy;
            }

            // Creamos comando de movimiento
            //String action = "MOVE " + playerId + " " + dx + " " + dy;

            // 4. Envío de la acción.
            HttpRequest actionReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8080/action"))
                    .POST(HttpRequest.BodyPublishers.ofString(action))
                    .build();

            client.send(actionReq, HttpResponse.BodyHandlers.ofString());

            // El servidor actualiza el estado del juego cada 100 milisegundos por lo que no tiene sentido enviar en un intervalo menor.
            Thread.sleep(100);
        }
    }
}