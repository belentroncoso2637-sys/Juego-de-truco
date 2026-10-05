// Trae Scanner, List, ArrayList, Random, Collections, Arrays y Comparator.
import java.util.*;

/**
 * TRUCO ARGENTINO por consola.
 *  - Modo 1 vs Bot
 *  - Modo 1 vs 1 (dos personas en la misma consola, se oculta la pantalla entre turnos)
 *
 * Compilar:  javac Truco.java
 * Ejecutar:  java Truco
 */
public class Truco {

    // Scanner único compartido por todo el programa para leer lo que escribe el usuario.
    static final Scanner IN = new Scanner(System.in);

    // ------------------------------------------------------------------
    //  Tipos básicos
    // ------------------------------------------------------------------
    // Los cuatro palos de la baraja española.
    enum Palo { ESPADA, BASTO, ORO, COPA }

    // Todas las acciones posibles: jugar carta, cantar envido/truco, responder y irse al mazo.
    enum Tipo {
        CARTA, ENVIDO, REAL_ENVIDO, FALTA_ENVIDO,
        TRUCO, RETRUCO, VALE_CUATRO,
        QUIERO, NO_QUIERO, MAZO
    }

    // En qué momento se le pide una decisión al jugador: su turno, o responder un envido / un truco.
    enum Situacion { TURNO, RESP_ENVIDO, RESP_TRUCO }

    // Una carta de la baraja: número (1 a 12, sin 8 ni 9) y palo.
    static class Carta {
        final int numero;
        final Palo palo;

        // Constructor: guarda el número y el palo.
        Carta(int numero, Palo palo) {
            this.numero = numero;
            this.palo = palo;
        }

        /** Jerarquía del truco (más alto = más fuerte). */
        int fuerza() {
            if (numero == 1 && palo == Palo.ESPADA) return 14;
            if (numero == 1 && palo == Palo.BASTO) return 13;
            if (numero == 7 && palo == Palo.ESPADA) return 12;
            if (numero == 7 && palo == Palo.ORO) return 11;
            // Jerarquía del resto de las cartas según su número.
            switch (numero) {
                case 3: return 10;
                case 2: return 9;
                case 1: return 8;   // 1 de oro / copa
                case 12: return 7;
                case 11: return 6;
                case 10: return 5;
                case 7: return 4;   // 7 de basto / copa
                case 6: return 3;
                case 5: return 2;
                default: return 1;  // 4
            }
        }

        // Valor de la carta para el envido: las figuras (10, 11, 12) valen 0 y el resto su número.
        int valorEnvido() {
            return numero >= 10 ? 0 : numero;
        }

        // Muestra la carta como texto, por ejemplo "7 de oro".
        @Override
        public String toString() {
            return numero + " de " + palo.name().toLowerCase();
        }
    }

    // Una opción que un jugador puede elegir (jugar una carta, cantar, responder, irse al mazo).
    static class Accion {
        final Tipo tipo;
        final int carta;      // índice en la mano (solo si tipo == CARTA)
        final String texto;

        Accion(Tipo tipo, int carta, String texto) {
            this.tipo = tipo;
            this.carta = carta;
            this.texto = texto;
        }
    }

    // Devuelve el texto que se muestra por pantalla para cada tipo de acción.
    static String texto(Tipo t) {
        switch (t) {
            case ENVIDO: return "Envido";
            case REAL_ENVIDO: return "Real Envido";
            case FALTA_ENVIDO: return "Falta Envido";
            case TRUCO: return "Truco";
            case RETRUCO: return "Retruco";
            case VALE_CUATRO: return "Vale Cuatro";
            case QUIERO: return "Quiero";
            case NO_QUIERO: return "No quiero";
            case MAZO: return "Irme al mazo";
            default: return "";
        }
    }

    // Atajo para crear una acción simple (sin carta) a partir de su tipo.
    static Accion acc(Tipo t) {
        return new Accion(t, -1, texto(t));
    }

    // Busca en la lista de acciones válidas una del tipo pedido; devuelve null si no hay.
    static Accion buscar(List<Accion> v, Tipo t) {
        for (Accion a : v) if (a.tipo == t) return a;
        return null;
    }

    // Pide un número por consola entre min y max y repite hasta que sea válido.
    static int leerEntero(int min, int max) {
        // Repite hasta recibir un número válido.
        while (true) {
            System.out.print("> ");
            // Lee la línea que escribió el usuario y le saca los espacios.
            String s = IN.nextLine().trim();
            try {
                // Intenta convertir el texto a número (si no es un número, salta NumberFormatException).
                int n = Integer.parseInt(s);
                // Si está dentro del rango permitido, lo devuelve; si no, muestra el error y vuelve a pedir.
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException e) {
                // cae al mensaje de error
            }
            System.out.println("Opción inválida. Elegí un número entre " + min + " y " + max + ".");
        }
    }

    // ------------------------------------------------------------------
    //  Jugadores
    // ------------------------------------------------------------------
    // Clase base de los jugadores. Humano y Bot la extienden y deciden de forma distinta.
    static abstract class Jugador {
        final String nombre;
        List<Carta> original = new ArrayList<>();  // las 3 cartas repartidas
        List<Carta> mano = new ArrayList<>();      // las que todavía tiene

        Jugador(String nombre) {
            this.nombre = nombre;
        }

        // Cada tipo de jugador implementa cómo elige una acción entre las válidas.
        abstract Accion elegir(Partida p, int yo, Situacion s, String contexto, List<Accion> validas);

        /** Puntos de envido con las 3 cartas repartidas. */
        int envido() {
            // Primero toma la carta suelta con mayor valor de envido.
            int mejor = 0;
            for (Carta c : original) mejor = Math.max(mejor, c.valorEnvido());
            // Prueba todos los pares de cartas: si son del mismo palo valen 20 + la suma de ambas.
            for (int i = 0; i < original.size(); i++) {
                for (int k = i + 1; k < original.size(); k++) {
                    Carta a = original.get(i), b = original.get(k);
                    if (a.palo == b.palo) {
                        mejor = Math.max(mejor, 20 + a.valorEnvido() + b.valorEnvido());
                    }
                }
            }
            return mejor;
        }
    }

    // Jugador controlado por una persona: muestra las opciones y lee lo que elige.
    static class Humano extends Jugador {
        Humano(String nombre) {
            super(nombre);
        }

        @Override
        Accion elegir(Partida p, int yo, Situacion s, String contexto, List<Accion> validas) {
            // En modo 1 vs 1 oculta la pantalla hasta que este jugador esté listo.
            p.prepararTurno(yo);
            // Muestra puntos, cartas en la mesa y las cartas de este jugador.
            p.mostrarMesa(yo);
            // Explica la situación (qué se cantó y cuánto vale).
            if (!contexto.isEmpty()) System.out.println(">> " + contexto);
            System.out.println("¿Qué hacés, " + nombre + "?");
            // Lista las acciones numeradas desde 1; a los cantos les antepone "Cantar".
            for (int i = 0; i < validas.size(); i++) {
                Accion a = validas.get(i);
                String t = a.texto;
                if (a.tipo == Tipo.ENVIDO || a.tipo == Tipo.REAL_ENVIDO || a.tipo == Tipo.FALTA_ENVIDO
                        || a.tipo == Tipo.TRUCO || a.tipo == Tipo.RETRUCO || a.tipo == Tipo.VALE_CUATRO) {
                    t = "Cantar " + t;
                }
                System.out.println("  " + (i + 1) + ") " + t);
            }
            // Lee la opción elegida y devuelve la acción correspondiente.
            int op = leerEntero(1, validas.size());
            return validas.get(op - 1);
        }
    }

    // Jugador controlado por la computadora: decide con reglas simples y algo de azar (faroles).
    static class Bot extends Jugador {
        // Generador de números aleatorios para decidir con probabilidades.
        final Random rnd = new Random();

        Bot(String nombre) {
            super(nombre);
        }

        @Override
        Accion elegir(Partida p, int yo, Situacion s, String contexto, List<Accion> v) {
            // Según la situación llama al método que corresponde.
            switch (s) {
                case RESP_ENVIDO: return respEnvido(p, v);
                case RESP_TRUCO: return respTruco(p, yo, v);
                default: return turno(p, yo, v);
            }
        }

        // Fuerza de la mejor carta que le queda en la mano.
        int mejor() {
            int m = 0;
            for (Carta c : mano) m = Math.max(m, c.fuerza());
            return m;
        }

        // Fuerza de la segunda mejor carta (0 si le queda una sola).
        int segunda() {
            List<Integer> f = new ArrayList<>();
            for (Carta c : mano) f.add(c.fuerza());
            f.sort(Collections.reverseOrder());
            return f.size() > 1 ? f.get(1) : 0;
        }

        // Decide qué hace el bot en su turno: cantar envido, cantar truco o jugar una carta.
        Accion turno(Partida p, int yo, List<Accion> v) {
            // Sus puntos de envido.
            int env = envido();
            // Si ya se jugó la primera baza, ve quién la ganó.
            boolean ganoPrimera = p.bazaActual > 0 && p.bazas[0] == yo;
            boolean perdioPrimera = p.bazaActual > 0 && p.bazas[0] == 1 - yo;

            // 1) Envido
            if (buscar(v, Tipo.ENVIDO) != null) {
                // Número al azar de 0 a 99: sirve para decidir con cierta probabilidad.
                int r = rnd.nextInt(100);
                if (env >= 31 && r < 70) return buscar(v, Tipo.REAL_ENVIDO);
                if (env >= 27 && r < 75) return buscar(v, Tipo.ENVIDO);
                if (env >= 22 && r < 8) return buscar(v, Tipo.ENVIDO); // farol
            }

            // 2) Truco / Retruco / Vale cuatro
            // Fuerza de su mejor y de su segunda mejor carta.
            int mejor = mejor(), seg = segunda();
            // Mano fuerte: carta muy alta, dos cartas buenas, o ganó la primera con una carta decente.
            boolean fuerte = mejor >= 13 || (mejor >= 10 && seg >= 9) || (ganoPrimera && mejor >= 9);
            int r2 = rnd.nextInt(100);
            // Busca cuáles de los cantos de truco puede hacer en este momento.
            Accion tr = buscar(v, Tipo.TRUCO);
            Accion re = buscar(v, Tipo.RETRUCO);
            Accion vc = buscar(v, Tipo.VALE_CUATRO);
            // Con mano fuerte canta con más probabilidad; a veces canta de farol.
            if (tr != null && ((fuerte && r2 < 45) || (!perdioPrimera && r2 < 5))) return tr;
            if (re != null && fuerte && r2 < 30) return re;
            if (vc != null && mejor >= 13 && (ganoPrimera || seg >= 10) && r2 < 30) return vc;

            // 3) Jugar carta
            return elegirCarta(p, yo, v);
        }

        // Elige qué carta jugar y devuelve la acción de jugarla.
        Accion elegirCarta(Partida p, int yo, List<Accion> v) {
            // Copia de la mano ordenada de la carta más débil a la más fuerte.
            List<Carta> orden = new ArrayList<>(mano);
            orden.sort(Comparator.comparingInt(Carta::fuerza));
            // Carta que ya puso el rival en esta baza (null si todavía no jugó).
            Carta rival = p.mesa[p.bazaActual][1 - yo];
            Carta elegida = null;

            if (rival != null) {
                // Respondo: la carta más baja que le gane
                for (Carta c : orden) {
                    if (c.fuerza() > rival.fuerza()) { elegida = c; break; }
                }
                if (elegida == null && p.bazaActual == 0) {
                    // si no puedo ganar, intento la parda
                    for (Carta c : orden) {
                        if (c.fuerza() == rival.fuerza()) { elegida = c; break; }
                    }
                }
                if (elegida == null) elegida = orden.get(0);
            } else {
                if (p.bazaActual == 0 && orden.size() >= 3) {
                    int r = rnd.nextInt(100);
                    elegida = orden.get(r < 30 ? 2 : (r < 80 ? 1 : 0));
                } else {
                    elegida = orden.get(orden.size() - 1);
                }
            }
            // Busca entre las acciones válidas la que juega la carta elegida.
            int idx = mano.indexOf(elegida);
            for (Accion a : v) if (a.tipo == Tipo.CARTA && a.carta == idx) return a;
            return v.get(0);
        }

        // Cómo responde el bot a un envido: puede subir, aceptar o rechazar.
        Accion respEnvido(Partida p, List<Accion> v) {
            int env = envido();
            int r = rnd.nextInt(100);
            if (env >= 32 && buscar(v, Tipo.FALTA_ENVIDO) != null && r < 40) return buscar(v, Tipo.FALTA_ENVIDO);
            if (env >= 30 && buscar(v, Tipo.REAL_ENVIDO) != null && r < 60) return buscar(v, Tipo.REAL_ENVIDO);
            if (env >= 28 && buscar(v, Tipo.ENVIDO) != null && r < 40) return buscar(v, Tipo.ENVIDO);
            // Puntos mínimos que necesita para aceptar; sube si lo cantado vale mucho.
            int umbral = 24;
            if (p.cadenaEnvido.contains(Tipo.FALTA_ENVIDO)) umbral = 29;
            else if (p.valorCadena(p.cadenaEnvido) >= 5) umbral = 27;
            if (env >= umbral) return acc(Tipo.QUIERO);
            if (r < 8) return acc(Tipo.QUIERO); // farol
            return acc(Tipo.NO_QUIERO);
        }

        // Cómo responde el bot a un truco: puede cantar envido primero, subir, aceptar o rechazar.
        Accion respTruco(Partida p, int yo, List<Accion> v) {
            int env = envido();
            // "El envido está primero"
            Accion e = buscar(v, Tipo.ENVIDO);
            if (e != null) {
                if (env >= 30 && buscar(v, Tipo.REAL_ENVIDO) != null) return buscar(v, Tipo.REAL_ENVIDO);
                if (env >= 26) return e;
            }
            int mejor = mejor(), seg = segunda();
            boolean ganoPrimera = p.bazaActual > 0 && p.bazas[0] == yo;
            boolean fuerte = mejor >= 13 || (mejor >= 10 && seg >= 9) || (ganoPrimera && mejor >= 9);
            // Mano intermedia: acepta solo a veces.
            boolean media = mejor >= 10 || ganoPrimera;
            int r = rnd.nextInt(100);
            // Guarda la opción de subir la apuesta (retruco o vale cuatro), si existe.
            Accion sube = buscar(v, Tipo.RETRUCO);
            if (sube == null) sube = buscar(v, Tipo.VALE_CUATRO);

            if (fuerte) {
                if (sube != null && r < 35 && (sube.tipo != Tipo.VALE_CUATRO || mejor >= 13)) return sube;
                return acc(Tipo.QUIERO);
            }
            if (media && r < 65) return acc(Tipo.QUIERO);
            if (r < 12) return acc(Tipo.QUIERO); // farol
            return acc(Tipo.NO_QUIERO);
        }
    }

    // ------------------------------------------------------------------
    //  Partida
    // ------------------------------------------------------------------
    // Maneja toda la partida: reparte, controla los turnos, aplica las reglas y lleva los puntos.
    static class Partida {
        // Los dos jugadores (posiciones 0 y 1).
        final Jugador[] j;
        // Puntos necesarios para ganar (15 o 30).
        final int objetivo;
        // true si juegan dos personas en la misma consola (se oculta la pantalla entre turnos).
        final boolean hotseat;
        // Sirve para mezclar el mazo y elegir quién es mano al principio.
        final Random rnd = new Random();
        // Puntos de cada jugador.
        final int[] puntos = new int[2];
        // Cantidad de manos ganadas por cada jugador.
        final int[] manosGanadas = new int[2];
        // Jugador (0 o 1) que es mano en la ronda actual; se alterna en cada mano.
        int manoIdx;
        // Pasa a true cuando alguien llega al objetivo.
        boolean partidaTerminada;

        // Estado de la mano en juego
        // Baza que se está jugando (0, 1 o 2).
        int bazaActual;
        final int[] bazas = new int[3];        // -1 sin jugar, 0/1 ganador, 2 parda
        // Cartas jugadas: mesa[baza][jugador].
        final Carta[][] mesa = new Carta[3][2];
        int trucoNivel;                        // 0 nada, 1 truco, 2 retruco, 3 vale cuatro (querido)
        int puedeSubir;                        // quién tiene derecho a subir el truco
        // true si el envido ya se jugó o ya no se puede cantar.
        boolean envidoResuelto;
        // true cuando la mano en juego ya tiene ganador.
        boolean manoTerminada;
        // Secuencia de cantos del envido actual (envido, real envido...).
        List<Tipo> cadenaEnvido = new ArrayList<>();
        // Último jugador al que se le mostró la pantalla (evita pedir Enter dos veces seguidas en 1 vs 1).
        int ultimoActor = -1;
        // Mensajes recientes de la mano, para mostrárselos al jugador que retoma la pantalla.
        final List<String> log = new ArrayList<>();

        // Resumen de la mano (se muestra al final de cada mano)
        // Quién ganó la mano (-1 si todavía nadie).
        int ganadorMano = -1;
        // Puntos que se llevó el ganador de la mano.
        int ptsMano;
        // Por qué ganó la mano (por bazas, truco no querido o mazo).
        String motivoMano = "";
        // Texto con el resultado del envido para el resumen final.
        String resumenEnvido = "no se cantó";
        // Texto con el resultado del truco para el resumen final.
        String resumenTruco = "no se cantó";

        // Crea la partida con los dos jugadores y elige al azar quién es mano primero.
        Partida(Jugador a, Jugador b, int objetivo, boolean hotseat) {
            this.j = new Jugador[]{a, b};
            this.objetivo = objetivo;
            this.hotseat = hotseat;
            this.manoIdx = rnd.nextInt(2);
        }

        // ---------- utilidades de salida ----------
        // Imprime un mensaje y lo guarda en el log.
        void say(String s) {
            System.out.println(s);
            log.add(s);
        }

        // Nombre del canto según el nivel: 1 Truco, 2 Retruco, 3 Vale Cuatro.
        String nombreTruco(int nivel) {
            return nivel == 1 ? "Truco" : nivel == 2 ? "Retruco" : "Vale Cuatro";
        }

        // Solo en 1 vs 1: borra la pantalla y espera Enter para que el otro jugador no vea las cartas.
        void prepararTurno(int yo) {
            // Contra el bot, o si el mismo jugador sigue jugando, no hace falta ocultar nada.
            if (!hotseat || ultimoActor == yo) return;
            // 40 líneas en blanco para "limpiar" la pantalla.
            for (int i = 0; i < 40; i++) System.out.println();
            System.out.println("*** TURNO DE " + j[yo].nombre.toUpperCase() + " ***  (que el otro jugador no mire la pantalla)");
            System.out.print("Presioná Enter cuando estés listo... ");
            IN.nextLine();
            // Recuerda a quién se le mostró la pantalla.
            ultimoActor = yo;
            // Muestra los últimos 6 eventos para que el jugador se ponga al día.
            if (!log.isEmpty()) {
                System.out.println("Últimos eventos:");
                for (int i = Math.max(0, log.size() - 6); i < log.size(); i++) {
                    if (!log.get(i).isEmpty()) System.out.println("  - " + log.get(i));
                }
            }
        }

        // Muestra el estado del juego desde el punto de vista del jugador yo (sus cartas y su envido).
        void mostrarMesa(int yo) {
            System.out.println();
            System.out.println("==================================================");
            System.out.printf("PUNTOS (a %d):  %s %d  |  %s %d%n", objetivo,
                    j[0].nombre, puntos[0], j[1].nombre, puntos[1]);
            System.out.printf("MANOS GANADAS:  %s %d  |  %s %d%n",
                    j[0].nombre, manosGanadas[0], j[1].nombre, manosGanadas[1]);
            System.out.println("Es mano en esta ronda: " + j[manoIdx].nombre);
            System.out.println("Truco: " + (trucoNivel == 0 ? "sin cantar"
                    : nombreTruco(trucoNivel) + " querido (la mano vale " + (trucoNivel + 1) + ")"));
            System.out.println("--- Mesa ---");
            // Indica si ya se jugó alguna carta en la mano.
            boolean hayCartas = false;
            // Bazas ganadas por cada jugador en esta mano.
            int w0 = 0, w1 = 0;
            // Muestra cada baza jugada con las cartas de ambos y quién la ganó.
            for (int i = 0; i <= Math.min(bazaActual, 2); i++) {
                if (mesa[i][0] == null && mesa[i][1] == null) continue;
                hayCartas = true;
                String a = mesa[i][0] == null ? "-" : mesa[i][0].toString();
                String b = mesa[i][1] == null ? "-" : mesa[i][1].toString();
                String res = "";
                if (bazas[i] == 2) res = "  -> parda";
                else if (bazas[i] >= 0) res = "  -> gana " + j[bazas[i]].nombre;
                if (bazas[i] == 0) w0++;
                if (bazas[i] == 1) w1++;
                System.out.println("Baza " + (i + 1) + ": " + j[0].nombre + " [" + a + "]  vs  "
                        + j[1].nombre + " [" + b + "]" + res);
            }
            if (!hayCartas) System.out.println("(todavía no se jugó ninguna carta)");
            System.out.println("Bazas ganadas en esta mano: " + j[0].nombre + " " + w0 + " | " + j[1].nombre + " " + w1);
            System.out.println("--- Tus cartas ---");
            // Las cartas que le quedan a este jugador.
            for (Carta c : j[yo].mano) System.out.println("   * " + c);
            // Solo muestra los puntos de envido mientras todavía se puede cantar.
            if (bazaActual == 0 && !envidoResuelto) {
                System.out.println("Tus puntos de envido: " + j[yo].envido());
            }
            System.out.println("==================================================");
        }

        // ---------- flujo de la partida ----------
        // Bucle principal: juega manos hasta que alguien llega al objetivo y anuncia al ganador.
        void jugar() {
            System.out.println("\n¡Empieza la partida a " + objetivo + " puntos! " + j[0].nombre + " vs " + j[1].nombre);
            while (!partidaTerminada) {
                jugarMano();
                // Cambia quién es mano para la siguiente ronda.
                if (!partidaTerminada) manoIdx = 1 - manoIdx;
            }
            // El ganador es el que llegó al objetivo.
            int g = puntos[0] >= objetivo ? 0 : 1;
            System.out.println();
            System.out.println("##################################################");
            System.out.println("  ¡GANÓ LA PARTIDA " + j[g].nombre.toUpperCase() + "!");
            System.out.printf("  Puntos: %s %d  |  %s %d%n", j[0].nombre, puntos[0], j[1].nombre, puntos[1]);
            System.out.printf("  Manos ganadas: %s %d  |  %s %d%n", j[0].nombre, manosGanadas[0], j[1].nombre, manosGanadas[1]);
            System.out.println("##################################################");
        }

        // Arma el mazo de 40 cartas (sin 8 ni 9), lo mezcla y reparte 3 a cada jugador.
        void repartir() {
            List<Carta> mazo = new ArrayList<>();
            // Números que tiene la baraja española de truco.
            int[] numeros = {1, 2, 3, 4, 5, 6, 7, 10, 11, 12};
            for (Palo p : Palo.values()) for (int n : numeros) mazo.add(new Carta(n, p));
            Collections.shuffle(mazo, rnd);
            for (int i = 0; i < 2; i++) {
                j[i].original = new ArrayList<>(mazo.subList(i * 3, i * 3 + 3));
                j[i].mano = new ArrayList<>(j[i].original);
            }
        }

        // Juega una mano completa: reparto, hasta 3 bazas y resumen final.
        void jugarMano() {
            // Reparte cartas nuevas.
            repartir();
            // Reinicia el estado de la mano: bazas sin jugar, mesa vacía, sin truco ni envido cantados.
            Arrays.fill(bazas, -1);
            for (Carta[] fila : mesa) Arrays.fill(fila, null);
            trucoNivel = 0;
            puedeSubir = -1;
            envidoResuelto = false;
            manoTerminada = false;
            cadenaEnvido = new ArrayList<>();
            ultimoActor = -1;
            ganadorMano = -1;
            ptsMano = 0;
            motivoMano = "";
            resumenEnvido = "no se cantó";
            resumenTruco = "no se cantó";
            log.clear();

            say("");
            say("=============== NUEVA MANO ===============");
            say("Es mano: " + j[manoIdx].nombre + "  |  Marcador: " + j[0].nombre + " " + puntos[0]
                    + " - " + j[1].nombre + " " + puntos[1]);

            // Quién empieza cada baza: en la primera el mano; después, quien ganó la anterior.
            int primero = manoIdx;
            // Recorre las bazas mientras la mano siga en juego.
            for (bazaActual = 0; bazaActual < 3 && !manoTerminada; bazaActual++) {
                // Juegan los dos jugadores, empezando por el que corresponde.
                for (int k = 0; k < 2 && !manoTerminada; k++) {
                    turno((primero + k) % 2);
                }
                // Si alguien se fue al mazo o llegó a los puntos, se corta la mano.
                if (manoTerminada) break;

                // Compara la fuerza de las dos cartas para decidir quién ganó la baza (2 = parda).
                int b = bazaActual;
                int f0 = mesa[b][0].fuerza(), f1 = mesa[b][1].fuerza();
                int res = f0 > f1 ? 0 : (f0 < f1 ? 1 : 2);
                bazas[b] = res;
                if (res == 2) say("Baza " + (b + 1) + ": PARDA (" + mesa[b][0] + " vs " + mesa[b][1] + ")");
                else say("Baza " + (b + 1) + ": gana " + j[res].nombre + " (" + mesa[b][0] + " vs " + mesa[b][1] + ")");

                // Comprueba si con esta baza ya se definió la mano.
                int g = ganadorDeMano(b);
                if (g >= 0) {
                    ganaMano(g, trucoNivel > 0 ? trucoNivel + 1 : 1, "ganó por bazas");
                    break;
                }
                // Empieza la próxima baza el que ganó esta; si fue parda, empieza el mano.
                primero = (res == 2) ? manoIdx : res;
            }
            // Muestra el resumen y espera Enter antes de la siguiente mano.
            mostrarResumenMano();
            if (!partidaTerminada) {
                System.out.print("Presioná Enter para empezar la siguiente mano... ");
                IN.nextLine();
            }
        }

        // Imprime el resumen de la mano que terminó: bazas, envido, truco, ganador y marcador.
        void mostrarResumenMano() {
            say("");
            say("================ RESUMEN DE LA MANO ================");
            for (int i = 0; i < 3; i++) {
                if (mesa[i][0] == null && mesa[i][1] == null) continue;
                String a = mesa[i][0] == null ? "-" : mesa[i][0].toString();
                String b = mesa[i][1] == null ? "-" : mesa[i][1].toString();
                String res = "";
                if (bazas[i] == 2) res = "  -> parda";
                else if (bazas[i] >= 0) res = "  -> gana " + j[bazas[i]].nombre;
                say("Baza " + (i + 1) + ": " + j[0].nombre + " [" + a + "]  vs  " + j[1].nombre + " [" + b + "]" + res);
            }
            say("ENVIDO: " + resumenEnvido);
            say("TRUCO:  " + resumenTruco);
            if (ganadorMano >= 0) {
                say("GANA LA MANO: " + j[ganadorMano].nombre.toUpperCase() + "  (+" + ptsMano + " punto"
                        + (ptsMano == 1 ? "" : "s") + ", motivo: " + motivoMano + ")");
            } else {
                say("La partida terminó durante la mano: alguien alcanzó los " + objetivo + " puntos.");
            }
            say("MARCADOR: " + j[0].nombre + " " + puntos[0] + " - " + j[1].nombre + " " + puntos[1]
                    + "  |  Manos ganadas: " + manosGanadas[0] + " - " + manosGanadas[1]);
            say("====================================================");
        }

        /** Devuelve el ganador de la mano después de la baza k, o -1 si sigue en juego. */
        int ganadorDeMano(int k) {
            int w0 = 0, w1 = 0;
            for (int i = 0; i <= k; i++) {
                if (bazas[i] == 0) w0++;
                else if (bazas[i] == 1) w1++;
            }
            // Quien tenga 2 bazas ganadas gana la mano.
            if (w0 >= 2) return 0;
            if (w1 >= 2) return 1;
            // Después de la 2ª baza: reglas de parda con la primera.
            if (k == 1) {
                if (bazas[0] == 2 && bazas[1] != 2) return bazas[1];
                if (bazas[0] != 2 && bazas[1] == 2) return bazas[0];
                return -1;
            }
            // Después de la 3ª baza: gana quien ganó la 3ª; si hubo parda, quien ganó la 1ª; si todo fue parda, el mano.
            if (k == 2) {
                if (bazas[2] != 2) return bazas[2];
                if (bazas[0] != 2) return bazas[0];
                return manoIdx;
            }
            return -1;
        }

        // Suma puntos al jugador p y termina la partida si llega al objetivo.
        void sumar(int p, int pts, String motivo) {
            puntos[p] += pts;
            say(">> " + j[p].nombre + " suma " + pts + " punto" + (pts == 1 ? "" : "s") + " (" + motivo + ")");
            if (puntos[p] >= objetivo) {
                partidaTerminada = true;
                manoTerminada = true;
            }
        }

        // Registra que el jugador g ganó la mano y le suma los puntos.
        void ganaMano(int g, int pts, String motivo) {
            manosGanadas[g]++;
            manoTerminada = true;
            ganadorMano = g;
            ptsMano = pts;
            motivoMano = motivo;
            sumar(g, pts, motivo);
        }

        // ---------- turno de un jugador ----------
        // Arma la lista de acciones que puede elegir el jugador p en su turno.
        List<Accion> accionesTurno(int p) {
            List<Accion> v = new ArrayList<>();
            // Siempre puede jugar cualquiera de las cartas que le quedan.
            List<Carta> mano = j[p].mano;
            for (int i = 0; i < mano.size(); i++) {
                v.add(new Accion(Tipo.CARTA, i, "Jugar " + mano.get(i)));
            }
            // El envido se puede cantar solo en la 1ª baza, si no se jugó ya y si no hay truco cantado.
            if (bazaActual == 0 && !envidoResuelto && trucoNivel == 0) {
                v.add(acc(Tipo.ENVIDO));
                v.add(acc(Tipo.REAL_ENVIDO));
                v.add(acc(Tipo.FALTA_ENVIDO));
            }
            // Truco si no hay nada cantado; retruco o vale cuatro solo si le toca subir.
            if (trucoNivel == 0) v.add(acc(Tipo.TRUCO));
            else if (trucoNivel == 1 && puedeSubir == p) v.add(acc(Tipo.RETRUCO));
            else if (trucoNivel == 2 && puedeSubir == p) v.add(acc(Tipo.VALE_CUATRO));
            // Siempre puede irse al mazo.
            v.add(acc(Tipo.MAZO));
            return v;
        }

        // Turno de un jugador: le pide una acción y la ejecuta (si canta algo, vuelve a pedir hasta que juegue carta).
        void turno(int p) {
            while (!manoTerminada) {
                List<Accion> v = accionesTurno(p);
                // El humano o el bot elige una de las acciones válidas.
                Accion a = j[p].elegir(this, p, Situacion.TURNO, "Es tu turno.", v);
                // Ejecuta la acción elegida.
                switch (a.tipo) {
                    // Juega la carta: la saca de la mano y la pone en la mesa.
                    case CARTA: {
                        Carta c = j[p].mano.remove(a.carta);
                        mesa[bazaActual][p] = c;
                        say(j[p].nombre + " juega " + c);
                        return;
                    }
                    // Se va al mazo: el rival gana la mano.
                    case MAZO:
                        say(j[p].nombre + " se va al mazo.");
                        ganaMano(1 - p, trucoNivel > 0 ? trucoNivel + 1 : 1, "el rival se fue al mazo");
                        return;
                    // Cantos de envido.
                    case ENVIDO:
                    case REAL_ENVIDO:
                    case FALTA_ENVIDO:
                        cantarEnvido(p, a.tipo);
                        break;
                    // Cualquier otro canto es truco, retruco o vale cuatro.
                    default:
                        cantarTruco(p, a.tipo);
                        break;
                }
            }
        }

        // ---------- ENVIDO ----------
        // Cuántos puntos vale la cadena de envidos cantada.
        int valorCadena(List<Tipo> c) {
            // Falta envido: lo que le falta al que va ganando para llegar al objetivo (mínimo 1).
            if (c.contains(Tipo.FALTA_ENVIDO)) {
                return Math.max(1, objetivo - Math.max(puntos[0], puntos[1]));
            }
            // Cada envido suma 2 y cada real envido suma 3.
            int s = 0;
            for (Tipo t : c) s += (t == Tipo.ENVIDO) ? 2 : 3;
            return s;
        }

        // Convierte la cadena de cantos en texto separado por comas.
        String describir(List<Tipo> c) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < c.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(texto(c.get(i)));
            }
            return sb.toString();
        }

        // Maneja la cadena de envidos: el otro jugador puede aceptar, rechazar o subir hasta que se resuelva.
        void cantarEnvido(int p, Tipo primero) {
            List<Tipo> cadena = new ArrayList<>();
            cadena.add(primero);
            cadenaEnvido = cadena;
            int quien = p;
            say(j[quien].nombre + ": ¡" + texto(primero) + "!");

            // Cada vuelta responde el jugador que no hizo el último canto.
            while (true) {
                // El que responde es el otro jugador.
                int r = 1 - quien;
                // Último canto hecho: determina cómo se puede subir.
                Tipo ultimo = cadena.get(cadena.size() - 1);
                List<Accion> v = new ArrayList<>();
                // Siempre puede aceptar o rechazar.
                v.add(acc(Tipo.QUIERO));
                v.add(acc(Tipo.NO_QUIERO));
                int cuantosEnvido = 0;
                for (Tipo t : cadena) if (t == Tipo.ENVIDO) cuantosEnvido++;
                // Tras un envido se puede subir con envido (máx. 2), real envido o falta envido; tras real envido, solo falta envido.
                if (ultimo == Tipo.ENVIDO) {
                    if (cuantosEnvido < 2) v.add(acc(Tipo.ENVIDO));
                    v.add(acc(Tipo.REAL_ENVIDO));
                    v.add(acc(Tipo.FALTA_ENVIDO));
                } else if (ultimo == Tipo.REAL_ENVIDO) {
                    v.add(acc(Tipo.FALTA_ENVIDO));
                }
                // Puntos que suma quien cantó si el otro no quiere.
                int perdida = cadena.size() == 1 ? 1 : valorCadena(cadena.subList(0, cadena.size() - 1));
                // Texto que se le muestra a quien responde.
                String ctx = j[quien].nombre + " cantó: " + describir(cadena) + ". Si querés se juegan "
                        + valorCadena(cadena) + " puntos; si no querés, " + perdida + " para "
                        + j[quien].nombre + ".";
                // El que responde elige su acción.
                Accion a = j[r].elegir(this, r, Situacion.RESP_ENVIDO, ctx, v);

                switch (a.tipo) {
                    // Aceptó: se comparan los puntos de envido.
                    case QUIERO:
                        say(j[r].nombre + ": ¡Quiero!");
                        resolverEnvido(cadena);
                        return;
                    // No quiso: el que cantó suma los puntos y se termina el envido.
                    case NO_QUIERO:
                        say(j[r].nombre + ": No quiero.");
                        envidoResuelto = true;
                        resumenEnvido = describir(cadena) + " NO QUERIDO -> " + j[quien].nombre
                                + " suma " + perdida + " punto" + (perdida == 1 ? "" : "s");
                        sumar(quien, perdida, "envido no querido");
                        return;
                    // Subió la apuesta: se agrega a la cadena y ahora le toca responder al otro.
                    default:
                        cadena.add(a.tipo);
                        say(j[r].nombre + ": ¡" + texto(a.tipo) + "!");
                        quien = r;
                        break;
                }
            }
        }

        // Compara los puntos de envido y da los puntos al ganador (habla primero el mano; en empate gana el mano).
        void resolverEnvido(List<Tipo> cadena) {
            // m = el mano, pie = el otro jugador.
            int m = manoIdx, pie = 1 - manoIdx;
            // Puntos de envido de cada uno.
            int em = j[m].envido(), ep = j[pie].envido();
            say(j[m].nombre + ": " + em + " puntos.");
            int ganador;
            // Gana el pie solo si tiene más puntos; si empatan gana el mano.
            if (ep > em) {
                say(j[pie].nombre + ": " + ep + " puntos, ¡son mejores!");
                ganador = pie;
            } else {
                say(j[pie].nombre + ": son buenas.");
                ganador = m;
            }
            envidoResuelto = true;
            // Muestra el resultado del envido y suma los puntos al ganador.
            int pts = valorCadena(cadena);
            boolean empate = (ep == em);
            say("");
            say("--------- RESULTADO DEL ENVIDO (" + describir(cadena) + ") ---------");
            say("  " + j[m].nombre + " (mano): " + em + " puntos");
            say("  " + j[pie].nombre + " (pie): " + ep + " puntos");
            say("  Gana el envido: " + j[ganador].nombre.toUpperCase() + (empate ? " (empate: gana el mano)" : "")
                    + "  -> +" + pts + " punto" + (pts == 1 ? "" : "s"));
            say("--------------------------------------------------");
            resumenEnvido = describir(cadena) + " querido -> " + j[m].nombre + " " + em + " vs " + j[pie].nombre + " " + ep
                    + (empate ? " (empate)" : "") + " -> gana " + j[ganador].nombre + " (+" + pts + ")";
            sumar(ganador, pts, "gana el envido");
        }

        // ---------- TRUCO ----------
        // Maneja truco, retruco y vale cuatro: el otro puede aceptar, rechazar, subir o (en la 1ª baza) cantar envido.
        void cantarTruco(int p, Tipo tipo) {
            // Nivel del canto: 1 truco, 2 retruco, 3 vale cuatro.
            int nivel = tipo == Tipo.TRUCO ? 1 : (tipo == Tipo.RETRUCO ? 2 : 3);
            say(j[p].nombre + ": ¡" + nombreTruco(nivel) + "!");
            // r es quien responde.
            int r = 1 - p;

            // Cada vuelta responde quien recibió el canto (acepta, rechaza, sube o canta envido).
            while (true) {
                List<Accion> v = new ArrayList<>();
                v.add(acc(Tipo.QUIERO));
                v.add(acc(Tipo.NO_QUIERO));
                // Puede subir un nivel, salvo que ya sea vale cuatro.
                if (nivel < 3) v.add(acc(nivel == 1 ? Tipo.RETRUCO : Tipo.VALE_CUATRO));
                // En la 1ª baza, con el envido sin jugar, puede responder con envido ("el envido está primero").
                boolean envidoPosible = bazaActual == 0 && !envidoResuelto && trucoNivel == 0 && nivel == 1;
                if (envidoPosible) {
                    v.add(acc(Tipo.ENVIDO));
                    v.add(acc(Tipo.REAL_ENVIDO));
                    v.add(acc(Tipo.FALTA_ENVIDO));
                }
                // Texto que se le muestra a quien responde.
                String ctx = j[p].nombre + " cantó " + nombreTruco(nivel).toUpperCase() + ". Si querés, la mano vale "
                        + (nivel + 1) + ". Si no querés, " + j[p].nombre + " suma " + nivel + ".";
                if (envidoPosible) ctx += " (Podés cantar envido antes: el envido va primero.)";
                // El que responde elige su acción.
                Accion a = j[r].elegir(this, r, Situacion.RESP_TRUCO, ctx, v);

                switch (a.tipo) {
                    // Aceptó: la mano pasa a valer nivel + 1 y ahora es él quien puede subir.
                    case QUIERO:
                        say(j[r].nombre + ": ¡Quiero!");
                        trucoNivel = nivel;
                        puedeSubir = r;
                        resumenTruco = nombreTruco(nivel) + " querido (la mano vale " + (nivel + 1) + ")";
                        return;
                    // No quiso: el que cantó gana la mano con los puntos del nivel anterior.
                    case NO_QUIERO:
                        say(j[r].nombre + ": No quiero.");
                        resumenTruco = nombreTruco(nivel) + " NO QUERIDO (" + j[p].nombre + " suma " + nivel + ")";
                        ganaMano(p, nivel, nombreTruco(nivel).toLowerCase() + " no querido");
                        return;
                    // Cantó envido antes de responder: se juega el envido y después vuelve a responder el truco.
                    case ENVIDO:
                    case REAL_ENVIDO:
                    case FALTA_ENVIDO:
                        cantarEnvido(r, a.tipo);
                        if (manoTerminada) return;
                        break; // vuelve a responder el truco
                    default:
                        // subir la apuesta: implica aceptar el nivel actual
                        trucoNivel = nivel;
                        puedeSubir = r;
                        cantarTruco(r, a.tipo);
                        return;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Menú principal
    // ------------------------------------------------------------------
    // Imprime las reglas del juego.
    static void mostrarAyuda() {
        System.out.println();
        System.out.println("--------------------- AYUDA ---------------------");
        System.out.println("Mazo español de 40 cartas (sin 8 ni 9). Se juegan 3 bazas; gana la mano quien gane 2.");
        System.out.println("Jerarquía (de mayor a menor):");
        System.out.println("  1 espada, 1 basto, 7 espada, 7 oro, los 3, los 2, 1 oro/copa,");
        System.out.println("  12, 11, 10, 7 basto/copa, 6, 5, 4");
        System.out.println("Parda (empate): si empatan la 1ª gana quien gane la 2ª; si empatan la 2ª gana quien");
        System.out.println("ganó la 1ª; si empatan la 3ª gana quien ganó la 1ª; si empatan todas gana el mano.");
        System.out.println("ENVIDO (solo en la 1ª baza, antes de jugar tu carta): 2 cartas del mismo palo = 20 + ambas");
        System.out.println("  (las figuras 10, 11 y 12 valen 0). Sin mismo palo cuenta tu carta más alta.");
        System.out.println("  Envido = 2, Real Envido = 3, Falta Envido = lo que le falta al líder para ganar.");
        System.out.println("  No querido = 1 punto (o lo cantado antes) para quien cantó. Empate: gana el mano.");
        System.out.println("TRUCO: Truco (2) -> Retruco (3) -> Vale Cuatro (4). Solo puede subir quien aceptó.");
        System.out.println("  No querido = suma el valor anterior. Irse al mazo regala la mano.");
        System.out.println("Cuando te cantan Truco en la 1ª baza podés responder con Envido: el envido va primero.");
        System.out.println("-------------------------------------------------");
    }

    // Punto de entrada: muestra el menú, crea los jugadores y arranca las partidas.
    public static void main(String[] args) {
        System.out.println("=====================================");
        System.out.println("        TRUCO ARGENTINO EN JAVA");
        System.out.println("=====================================");

        // Bucle del menú: se repite hasta que el usuario elige salir.
        while (true) {
            System.out.println();
            System.out.println("1) Jugar contra el Bot");
            System.out.println("2) Jugar 1 vs 1 (dos personas en esta consola)");
            System.out.println("3) Reglas y ayuda");
            System.out.println("0) Salir");
            // Opción del menú: 0 salir, 1 vs bot, 2 vs persona, 3 ayuda.
            int op = leerEntero(0, 3);
            if (op == 0) {
                System.out.println("¡Hasta la próxima!");
                return;
            }
            if (op == 3) {
                mostrarAyuda();
                continue;
            }

            // a y b son los dos jugadores de la partida.
            Jugador a, b;
            // hotseat = true si juegan dos personas en la misma consola.
            boolean hotseat = (op == 2);
            System.out.print("Nombre del jugador 1: ");
            // Si no escribe nombre, se usa uno por defecto.
            String n1 = IN.nextLine().trim();
            if (n1.isEmpty()) n1 = "Jugador 1";
            // El jugador 1 siempre es una persona.
            a = new Humano(n1);
            // El jugador 2 es otra persona (1 vs 1) o el bot.
            if (hotseat) {
                System.out.print("Nombre del jugador 2: ");
                String n2 = IN.nextLine().trim();
                if (n2.isEmpty() || n2.equalsIgnoreCase(n1)) n2 = "Jugador 2";
                b = new Humano(n2);
            } else {
                b = new Bot("Bot");
            }

            System.out.println("¿A cuántos puntos se juega?  1) 15   2) 30");
            // Puntos para ganar: 15 o 30.
            int objetivo = leerEntero(1, 2) == 1 ? 15 : 30;

            // Crea y juega la partida; al terminar vuelve al menú.
            new Partida(a, b, objetivo, hotseat).jugar();
        }
    }
}