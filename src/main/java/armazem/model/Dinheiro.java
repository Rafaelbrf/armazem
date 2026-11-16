package armazem.model;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/*
 Ajuda a trabalhar com dinheiro.
 Por dentro, o valor é sempre um número inteiro de centavos (1250 significa R$ 12,50).
 */
public class Dinheiro {

    private static final long MAXIMO = 9999999999L;   // R$ 99.999.999,99 em centavos

    /*
     Transforma o texto digitado em centavos.
     Aceita "12,50", "12.50", "1.234,50" e "12". Devolve -1 se o texto não for um valor válido
     (vazio, letras, negativo ou com mais de 2 casas depois da vírgula).
     */
    public static long paraCentavos(String texto) {
        if (texto == null) {
            return -1;
        }
        texto = texto.trim();
        if (texto.equals("")) {
            return -1;
        }
        // se tem vírgula, o ponto é separador de milhar (1.234,50), então tiramos o ponto
        if (texto.contains(",")) {
            texto = texto.replace(".", "").replace(",", ".");
        }
        try {
            BigDecimal valor = new BigDecimal(texto);
            if (valor.signum() < 0 || valor.scale() > 2) {
                return -1;
            }
            long centavos = valor.movePointRight(2).longValueExact();
            return centavos > MAXIMO ? -1 : centavos;
        } catch (Exception e) {
            return -1;
        }
    }

    // Mostra o valor para as pessoas, por exemplo: R$ 1.234,50
    public static String formatar(long centavos) {
        NumberFormat formato = NumberFormat.getInstance(Locale.forLanguageTag("pt-BR"));
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return "R$ " + formato.format(new BigDecimal(centavos).movePointLeft(2));
    }

    // Texto para colocar dentro do campo do formulário, por exemplo: 12,50
    public static String paraCampo(long centavos) {
        return String.format("%d,%02d", centavos / 100, centavos % 100);
    }
}
