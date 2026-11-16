package armazem.model;

/*
 Uma linha do histórico: guarda QUEM fez O QUE, QUANDO e em qual produto.
 */
public class Registro {

    private String dataHora;   // quando aconteceu (ex: 14/11/2026 15:30:12)
    private String usuario;    // nome de quem fez
    private String codigo;     // código (ID) de quem fez, por exemplo GER-0002
    private String acao;       // o que fez (ex: "Criou produto")
    private int produtoId;     // id do produto envolvido (0 significa nenhum produto)
    private String detalhes;   // informações extras (por exemplo: quantidade passou de 4 para 10)

    public Registro(String dataHora, String usuario, String codigo, String acao,
                    int produtoId, String detalhes) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.codigo = codigo;
        this.acao = acao;
        this.produtoId = produtoId;
        this.detalhes = detalhes;
    }

    public String getDataHora() { return dataHora; }
    public String getUsuario() { return usuario; }
    public String getCodigo() { return codigo; }
    public String getAcao() { return acao; }
    public int getProdutoId() { return produtoId; }
    public String getDetalhes() { return detalhes; }
}
