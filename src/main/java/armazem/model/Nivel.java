package armazem.model;

/*
 Os cargos (níveis) do sistema, do menor para o maior:
   1 Operador, 2 Supervisor, 3 Gerente e 4 ADM.
 O ADM é o maior cargo, e pode existir mais de um ADM.
 Quem tem cargo maior manda em quem tem cargo menor.

 Aqui também ficam as siglas dos cargos, usadas no código (ID) de cada usuário.
 */
public class Nivel {

    public static final int OPERADOR = 1;
    public static final int SUPERVISOR = 2;
    public static final int GERENTE = 3;
    public static final int ADM = 4;

    // a posição 0 não é usada, assim o cargo número 1 fica na posição 1 e assim por diante
    private static final String[] NOMES = {"", "Operador", "Supervisor", "Gerente", "ADM"};
    private static final String[] SIGLAS = {"", "OPE", "SUP", "GER", "ADM"};

    // Diz se o número é um cargo que existe (de 1 a 4)
    public static boolean valido(int nivel) {
        return nivel >= OPERADOR && nivel <= ADM;
    }

    public static String nome(int nivel) {
        return valido(nivel) ? NOMES[nivel] : "Desconhecido";
    }

    public static String sigla(int nivel) {
        return valido(nivel) ? SIGLAS[nivel] : "USU";
    }

    /*
     Monta o código (ID) do usuário: sigla do cargo, um traço e o número de entrada.
     Exemplo: o 3º usuário que entrou no sistema, sendo Gerente, é GER-0003.
     O número de entrada nunca se repete, então o código também nunca se repete,
     mesmo que dois usuários tenham o mesmo cargo.
     */
    public static String gerarCodigo(int nivel, int numeroDeEntrada) {
        return sigla(nivel) + "-" + String.format("%04d", numeroDeEntrada);
    }
}
