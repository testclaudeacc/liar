package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Scaling;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Conteudo de uma bag de loot no chao (resposta do request_loot): itens +
 * moedas, com "Take all" (collect_loot) e "Close".
 */
public final class JanelaLoot {

    private final Table janela = new Table();
    private final Table conteudo = new Table();
    private final Skin skin;
    private final TextureAtlas atlas;
    private final Function<String, TextureRegion> iconeDoItem;
    private String lootId = "";

    public JanelaLoot(Stage stage, Skin skin, TextureAtlas atlas,
                      Function<String, TextureRegion> iconeDoItem, Consumer<String> aoPegar) {
        this.skin = skin;
        this.atlas = atlas;
        this.iconeDoItem = iconeDoItem;

        janela.setBackground(UiSkin.retangulo(new Color(0.125f, 0.125f, 0.125f, 0.97f), UiSkin.COR_BORDA_PAINEL, 1));
        janela.pad(10);
        Label titulo = new Label("Loot", skin, "hud");
        conteudo.top().left();

        TextButton.TextButtonStyle estiloVerde = new TextButton.TextButtonStyle(skin.get("verde", TextButton.TextButtonStyle.class));
        estiloVerde.font = skin.getFont("botao-pequeno-font");
        TextButton.TextButtonStyle estiloCinza = new TextButton.TextButtonStyle(skin.get("default", TextButton.TextButtonStyle.class));
        estiloCinza.font = skin.getFont("botao-pequeno-font");
        TextButton pegar = new TextButton("Take all", estiloVerde);
        pegar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (!lootId.isEmpty()) aoPegar.accept(lootId);
                fechar();
            }
        });
        TextButton fechar = new TextButton("Close", estiloCinza);
        fechar.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                fechar();
            }
        });

        janela.add(titulo).left().padBottom(6).row();
        janela.add(conteudo).growX().left().padBottom(8).row();
        Table botoes = new Table();
        botoes.add(pegar).width(110).height(34).padRight(6);
        botoes.add(fechar).width(90).height(34);
        janela.add(botoes);

        Table centro = new Table();
        centro.setFillParent(true);
        centro.add(janela);
        stage.addActor(centro);
        janela.setVisible(false);
    }

    /** Monta a janela com a resposta do servidor (loot_result). */
    public void mostrar(JsonValue resultado) {
        lootId = resultado.getString("loot_id", "");
        conteudo.clearChildren();
        if (resultado.getBoolean("already_taken", false)) {
            conteudo.add(new Label("This bag is empty.", skin, "hud")).left();
            lootId = "";
            janela.setVisible(true);
            return;
        }
        Table itens = new Table();
        int n = 0;
        JsonValue lista = resultado.get("items");
        if (lista != null) {
            for (JsonValue item = lista.child; item != null; item = item.next) {
                Table slot = new Table();
                slot.setBackground(UiSkin.retangulo(new Color(0.15f, 0.15f, 0.15f, 1f), new Color(0.21f, 0.21f, 0.21f, 1f), 2));
                Stack pilha = new Stack();
                TextureRegion icone = iconeDoItem.apply(item.getString("item", ""));
                if (icone != null) {
                    Image img = new Image(new TextureRegionDrawable(icone));
                    img.setScaling(Scaling.fit);
                    pilha.add(img);
                }
                int qty = item.getInt("qty", 1);
                if (qty > 1) {
                    Table canto = new Table();
                    canto.bottom().left();
                    Label q = new Label(String.valueOf(qty), skin, "hud");
                    q.setFontScale(0.6f);
                    canto.add(q);
                    pilha.add(canto);
                }
                slot.add(pilha).grow().pad(4);
                itens.add(slot).size(44).pad(1.5f);
                if (++n % 6 == 0) itens.row();
            }
        }
        if (n > 0) conteudo.add(itens).left().padBottom(6).row();
        long moedas = resultado.getLong("currency", 0L);
        if (moedas > 0) conteudo.add(linhaMoedas(moedas)).left().row();
        if (n == 0 && moedas <= 0) conteudo.add(new Label("This bag is empty.", skin, "hud")).left();
        janela.setVisible(true);
    }

    /** Mesma divisao da Bag: 100 copper = 1 silver, 100 silver = 1 gold, 100 gold = 1 platinum. */
    private Table linhaMoedas(long total) {
        long[] valores = {total / 1_000_000L, (total % 1_000_000L) / 10_000L, (total % 10_000L) / 100L, total % 100L};
        String[] tipos = {"Platinum", "Gold", "Silver", "Copper"};
        Table linha = new Table();
        for (int i = 0; i < tipos.length; i++) {
            if (valores[i] <= 0) continue;
            TextureAtlas.AtlasRegion icone = atlas.findRegion("ui/currency/" + tipos[i]);
            if (icone != null) {
                Image img = new Image(new TextureRegionDrawable(icone));
                img.setScaling(Scaling.fit);
                linha.add(img).size(20).padRight(3);
            }
            Label l = new Label(String.valueOf(valores[i]), skin, "hud");
            l.setFontScale(0.8f);
            linha.add(l).padRight(10);
        }
        return linha;
    }

    public void fechar() {
        janela.setVisible(false);
        lootId = "";
    }

    /** A bag sumiu (pega/expirou): fecha se era essa que estava aberta. */
    public void bagRemovida(String id) {
        if (id.equals(lootId)) fechar();
    }

    public boolean isVisible() {
        return janela.isVisible();
    }
}
