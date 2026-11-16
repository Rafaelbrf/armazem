package armazem.model;

/*
 Representa um produto do armazém.
 Cada produto tem: id (gerado pelo banco), nome, tipo, quantidade e valor.

 O valor é guardado em CENTAVOS, como número inteiro (1250 significa R$ 12,50).
 Assim não acontecem erros de arredondamento que números com vírgula podem causar.
 */
public class Produto {

    // atributos privados (encapsulamento): só se acessa pelos métodos get
    private int id;
    private String nome;
    private String tipo;
    private int quantidade;
    private long valorCentavos;

    // construtor: cria um produto já com todos os dados
    public Produto(int id, String nome, String tipo, int quantidade, long valorCentavos) {
        this.id = id;
        this.nome = nome;
        this.tipo = tipo;
        this.quantidade = quantidade;
        this.valorCentavos = valorCentavos;
    }

    // getters: devolvem o valor de cada atributo
    public int getId() { return id; }
    public String getNome() { return nome; }
    public String getTipo() { return tipo; }
    public int getQuantidade() { return quantidade; }
    public long getValorCentavos() { return valorCentavos; }
}
