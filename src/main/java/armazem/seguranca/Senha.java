package armazem.seguranca;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/*
 Cuida da segurança das senhas.

 Não guardamos a senha pura, porque se alguém roubasse o banco veria todas as senhas.
 Então guardamos um "hash": um código embaralhado que não dá para desfazer.
 Para conferir o login, geramos o hash da senha digitada e comparamos com o salvo.
 */
public class Senha {

    private static final SecureRandom aleatorio = new SecureRandom();

    // Gera um "salt": texto aleatório que deixa o hash de cada usuário diferente.
    public static String novoSalt() {
        byte[] bytes = new byte[16];
        aleatorio.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Transforma (senha + salt) em um hash usando o algoritmo PBKDF2.
    public static String gerarHash(String senha, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), saltBytes, 120000, 256);
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = fabrica.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar o hash da senha", e);
        }
    }

    // Gera um código aleatório que identifica quem está logado (vai no cookie).
    public static String novoToken() {
        byte[] bytes = new byte[32];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
