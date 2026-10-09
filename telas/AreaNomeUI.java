package com.teste.game.telas;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;

/** Banner "entrou numa area nova" (nome centralizado, icone dos 2 lados,
 * linha embaixo, fade in -> segura -> fade out) estilo Dark Souls, a pedido
 * do usuario (ver print "Effect.png" que ele mandou - "Firelink Shrine").
 * Quem decide QUANDO mostrar e' o WorldScreen (ver areaAtual/
 * MapaPropriedades.areaEm()); esta classe so' cuida da animacao/layout. */
public final class AreaNomeUI {

    private static final float DURACAO_FADE_IN = 0.6f;
    private static final float DURACAO_SEGURA = 2.4f;
    private static final float DURACAO_FADE_OUT = 1f;
    // Folga na linha alem da largura do texto.
    private static final float FOLGA_LINHA = 70f;

    // "^" reaproveitado como icone inline (mesmo truque de DialogoNPCUI,
    // ver comentario no construtor) - cada instancia tem sua PROPRIA fonte,
    // entao remapear "^" aqui nao afeta o "^" de DialogoNPCUI nem o de
    // nenhum texto "default"/"subtitulo" do resto do jogo.
    private static final char ICONE = '^';

    private final Table root = new Table();
    private final Label label;
    private final Image linhaBaixo, linhaCima;

    public AreaNomeUI(Stage stage, Skin skin, TextureRegion iconeLados, float escalaFonte) {
        // Textura 1x1 branca reaproveitada (tintada via Image.setColor) pra
        // linha fina abaixo do nome - mesmo truque usado em qualquer
        // "retangulo solido" de UI, só que sem precisar passar pelo Skin (a
        // cor/largura mudam dinamicamente, ver mostrar()).
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        TextureRegionDrawable linhaDrawable = new TextureRegionDrawable(new TextureRegion(new Texture(pixmap)));
        pixmap.dispose();

        // Fonte PROPRIA (nao a "subtitulo" compartilhada do skin) - precisa
        // de instancia dedicada pra poder: (1) usar um tamanho proprio (40px
        // - um pouco maior que "subtitulo"/34px, a pedido do usuario) e (2)
        // remapear o "^" pro icone (caveira, "sheet/r83_c11" do
        // graphics.png, pedido pelo usuario pela coordenada de pixel) so'
        // aqui, sem bagunçar "^" em nenhum outro texto do jogo que use a
        // fonte compartilhada.
        BitmapFont fonteNome = UiSkin.gerarFonte("fonts/martel.ttf", 40, 2, escalaFonte);
        // NAO reusa getGlyph('^') (glifo JA' EXISTENTE da fonte) - martel.ttf
        // aparentemente nao tem glifo de verdade pro caractere "^" (FreeType
        // so' cria o glyph se a fonte tiver um outline pra aquele codepoint),
        // entao getGlyph('^') voltava null e o icone nunca era remapeado -
        // o Label caia no "missing glyph" padrao (o quadradinho reportado
        // pelo usuario). Cria um Glyph do ZERO (sem depender de a fonte ja'
        // ter esse caractere) e registra com setGlyph() - funciona com
        // QUALQUER fonte, nao so' as que por acaso ja' tem "^".
        BitmapFont.Glyph glifoIcone = new BitmapFont.Glyph();
        glifoIcone.id = ICONE;
        com.badlogic.gdx.utils.Array<TextureRegion> paginas = fonteNome.getRegions();
        int indicePagina = paginas.size;
        TextureRegion paginaIcone = new TextureRegion(iconeLados);
        paginas.add(paginaIcone);
        glifoIcone.page = indicePagina;
        glifoIcone.srcX = 0;
        glifoIcone.srcY = 0;
        glifoIcone.width = paginaIcone.getRegionWidth();
        glifoIcone.height = paginaIcone.getRegionHeight();
        fonteNome.getData().setGlyphRegion(glifoIcone, paginaIcone);
        // Tamanho/alinhamento copiados do glifo de "A" (ja' renderiza certo)
        // - igual DialogoNPCUI, evita o icone flutuar acima/abaixo da linha
        // do texto por causa de um yoffset chutado.
        BitmapFont.Glyph referencia = fonteNome.getData().getGlyph('A');
        int ladoGlifo = referencia != null ? referencia.height : Math.round(24f * escalaFonte);
        glifoIcone.width = ladoGlifo;
        glifoIcone.height = ladoGlifo;
        glifoIcone.xoffset = 0;
        glifoIcone.yoffset = referencia != null ? referencia.yoffset : 0;
        glifoIcone.xadvance = ladoGlifo;
        glifoIcone.kerning = null;
        fonteNome.getData().setGlyph(ICONE, glifoIcone);

        Label.LabelStyle estiloNome = new Label.LabelStyle(fonteNome, Color.WHITE);
        label = new Label("", estiloNome);
        label.setAlignment(Align.center);

        linhaBaixo = new Image(linhaDrawable);
        linhaBaixo.setColor(1f, 1f, 1f, 0.85f);
        linhaCima = new Image(linhaDrawable);
        linhaCima.setColor(1f, 1f, 1f, 0.85f);

        // Linha em cima e embaixo do nome.
        root.add(linhaCima).height(2f).padBottom(10f).row();
        root.add(label).padBottom(14f).row();
        root.add(linhaBaixo).height(2f);

        root.setFillParent(true);
        // Um pouco acima do centro da tela, igual Dark Souls (a pedido do
        // usuario) - o padBottom empurra o bloco centralizado pra cima.
        root.center().padBottom(300f);
        // So' leitura - nao deve roubar clique/toque de nada atras dela.
        root.setTouchable(Touchable.disabled);
        root.getColor().a = 0f;
        root.setVisible(false);
        stage.addActor(root);
    }

    /** Dispara a animacao pro nome dado - chamado so' quando o jogador ENTRA
     * numa area nomeada nova (ver WorldScreen), nunca a cada frame. Chamar
     * de novo no meio de uma animacao em curso reinicia ela (corta o fade
     * anterior e comeca do zero com o novo nome). */
    public void mostrar(String nome) {
        // So' o nome (sem os icones dos lados, a pedido do usuario).
        label.setText(nome);
        // Nao precisa de root.pack() - Label recalcula getPrefWidth() sozinho
        // quando o texto muda (GlyphLayout interno), e pack() aqui brigaria
        // com setFillParent(true) (tentaria encolher o root pro tamanho
        // preferido, so' pra ExtendViewport/Table.validate() desfazer isso
        // de volta no frame seguinte).
        float largura = label.getPrefWidth() + FOLGA_LINHA;
        root.getCell(linhaBaixo).width(largura);
        root.getCell(linhaCima).width(largura);
        root.invalidateHierarchy();

        root.clearActions();
        root.setVisible(true);
        root.getColor().a = 0f;
        root.addAction(Actions.sequence(
            Actions.fadeIn(DURACAO_FADE_IN),
            Actions.delay(DURACAO_SEGURA),
            Actions.fadeOut(DURACAO_FADE_OUT),
            Actions.visible(false)
        ));
    }
}
