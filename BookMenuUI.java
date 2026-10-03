package com.teste.game.telas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.Scaling;
import com.teste.game.rede.GameSocket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BookMenuUI {

    // Diametro do botao redondo das laterais + "aba" reta que liga o circulo
    // ao painel (ver prints/tela desejada.png).
    private static final float BOTAO_SIZE = 74f;
    private static final float BOTAO_ABA = 8f;
    private static final float COLUNA_LARGURA = BOTAO_SIZE + BOTAO_ABA;
    private static final float JANELA_LARGURA = 520f;
    private static final float JANELA_ALTURA = 410f;
    private static final int COLUNAS_INVENTARIO = 7;

    private final Table root = new Table();
    private final Map<String, Button> botoes = new LinkedHashMap<>();
    private final Label tituloSecao;
    private final Skin skin;
    private final TextureAtlas atlas;
    private final GameSocket socket;
    private final String classeJogador;
    private final Table mainWindow = new Table();
    private final Table bagPage = new Table();
    private final Table equipPage = new Table();
    private final Table skillsPage = new Table();
    private final Table inventoryGrid = new Table();
    private final Label itemName;
    private final Label itemQuantity;
    private final Label itemFavorite;
    private final Label capacityLabel;
    private final Label actionStatus;
    private final Map<String, Label> currencyLabels = new LinkedHashMap<>();
    private final List<InventoryItem> inventoryItems = new ArrayList<>();
    private Button favoriteButton;
    private Button trashButton;
    private Table actionButtons;
    private Table deleteConfirmation;
    private Table actionBar;
    private boolean confirmandoExclusao = false;
    private int selectedItem = -1;
    private String selectedInstanceId = "";
    private long currencyTotal;
    private float capacityUsed;
    private float capacityMaximum;
    private String secaoAtual = "Bag";

    // --- Equip ---
    // Grade 3x3 da coluna esquerda (null = celula vazia), igual prints/tela desejada.png.
    private static final String[][] SLOTS_EQUIP = {
        {null, "Helm", "Necklace"}, {"MainHand", "Chest", "Hand"}, {"Gloves", "Boots", "Ring"}
    };
    private static final float SLOT_EQUIP = 54f;
    private static final float COLUNA_EQUIP = 180f;
    private static final float ALTURA_GRADE_EQUIP = 216f;
    private static final int COLUNAS_EQUIP = 5;
    private final Map<String, Button> equipSlotButtons = new LinkedHashMap<>();
    private final Map<String, String> equippedItemPaths = new LinkedHashMap<>();
    private final List<Integer> equipCandidatos = new ArrayList<>();
    private final Table equipItemGrid = new Table();
    private final Table equipDetalhesEsquerda = new Table();
    private final Table equipDetalhesDireita = new Table();
    private Table equipColunaEsquerda;
    private Table equipSlotsTabela;
    private Table equipColunaDireita;
    private Table equipPainelGrade;
    private TextButton equipButton;
    private TextButton unequipButton;
    private String selectedSlot = "";
    private int selectedEquipCandidate = -1;

    // --- Skills ---
    private BarraSkill barraNivel;
    private BarraSkill barraPrincipal;
    private BarraSkill barraDefesa;
    private BarraSkill barraFome;
    private final Map<String, Label> valoresStatus = new LinkedHashMap<>();
    private JsonValue ultimasSkills;
    private int nivelAtual = 1;
    private int expAtual = 0;
    private int killsAtual = 0;
    private static final float LARGURA_BARRA_SKILL = 285f;
    private static final float ALTURA_BARRA_SKILL = 24f;
    private static final float LARGURA_NOME_SKILL = 90f;
    private static final float TAMANHO_ICONE_SKILL = 24f;
    private static final float TAMANHO_ICONE_STATUS = 20f;
    private static final float LARGURA_ROTULO_STATUS = 124f;
    private static final float ALTURA_TOPO_SKILLS = 160f;

    public BookMenuUI(Stage stage, Skin skin, TextureAtlas atlas, GameSocket socket, String classeJogador) {
        this.skin = skin;
        this.atlas = atlas;
        this.socket = socket;
        this.classeJogador = classeJogador == null ? "Knight" : classeJogador;
        skin.add("book-button", UiSkin.retangulo(
            new Color(0.08f, 0.09f, 0.10f, 0.98f), UiSkin.COR_BORDA_PAINEL, 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("book-button-hover", UiSkin.retangulo(
            new Color(0.12f, 0.22f, 0.22f, 1f), new Color(0.25f, 0.7f, 0.62f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("book-button-selected", UiSkin.retangulo(
            new Color(0.08f, 0.38f, 0.32f, 1f), new Color(0.35f, 0.92f, 0.72f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot", UiSkin.retangulo(
            new Color(0.025f, 0.028f, 0.03f, 1f), new Color(0.12f, 0.13f, 0.14f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot-hover", UiSkin.retangulo(
            new Color(0.07f, 0.08f, 0.085f, 1f), new Color(0.26f, 0.28f, 0.3f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-slot-selected", UiSkin.retangulo(
            new Color(0.17f, 0.2f, 0.2f, 1f), new Color(0.95f, 0.65f, 0.24f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("bag-detail", UiSkin.retangulo(
            new Color(0.025f, 0.028f, 0.03f, 1f), new Color(0.12f, 0.13f, 0.14f, 1f), 1),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);

        Table organizer = new Table() {
            @Override
            public void layout() {
                super.layout();
                setOrigin(getWidth() / 2f, getHeight() / 2f);
            }
        };
        Table leftTab = criarColuna(atlas, new String[][]{
            {"Party", "ui/buttons/PartyBtn"},
            {"Friends", "ui/buttons/FriendsBtn"},
            {"Map", "ui/buttons/MapBtn"},
            {"Rank", "ui/buttons/RankBtn"},
            {"Exit", "ui/buttons/CloseBtn"}
        }, true);
        Table rightTab = criarColuna(atlas, new String[][]{
            {"Equip", "ui/buttons/EquipMenuBtn"},
            {"Bag", "ui/buttons/InventoryBtn"},
            {"Skills", "ui/buttons/SkillsBtn"},
            {"Vanity", "ui/buttons/SkinsBtn"},
            {"Spells", "ui/buttons/SpellsBtn"}
        }, false);

        mainWindow.setBackground(UiSkin.retangulo(
            new Color(0.125f, 0.125f, 0.125f, 0.98f), UiSkin.COR_BORDA_PAINEL, 1));
        mainWindow.pad(14);
        mainWindow.top().left();
        tituloSecao = new Label("Bag", skin, "subtitulo");
        itemName = new Label("", skin, "default");
        itemName.setWrap(true);
        itemQuantity = new Label("", skin, "hud");
        itemFavorite = new Label("", skin, "hud");
        capacityLabel = new Label("Capacity: 0 / 0", skin, "hud");
        actionStatus = new Label("", skin, "hud");
        construirPaginaBag(skin);
        construirPaginaEquip(skin);
        atualizarEquipados(null);
        construirPaginaSkills(skin);
        mainWindow.add(bagPage).grow();

        organizer.add(leftTab).width(COLUNA_LARGURA).height(JANELA_ALTURA);
        organizer.add(mainWindow).width(JANELA_LARGURA).height(JANELA_ALTURA);
        organizer.add(rightTab).width(COLUNA_LARGURA).height(JANELA_ALTURA);
        organizer.setTransform(true);
        boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
            || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS;
        organizer.setScale(mobile ? 1.2f : 1f);

        root.setFillParent(true);
        root.center();
        root.add(organizer).width(COLUNA_LARGURA * 2f + JANELA_LARGURA)
            .height(JANELA_ALTURA);
        stage.addActor(root);
        registrarAtalhos(stage);
        root.setVisible(false);
        selecionarSecao(secaoAtual);
    }

    private Table criarColuna(TextureAtlas atlas, String[][] secoes, boolean esquerda) {
        Table coluna = new Table();
        coluna.top();
        float espaco = (JANELA_ALTURA - BOTAO_SIZE * secoes.length) / Math.max(1, secoes.length - 1);
        for (int i = 0; i < secoes.length; i++) {
            String[] secao = secoes[i];
            String nome = secao[0];
            Color cor = corDaSecao(nome);
            Button.ButtonStyle estilo = new Button.ButtonStyle();
            estilo.up = botaoRedondo(cor.cpy().mul(0.22f, 0.22f, 0.22f, 1f),
                cor.cpy().mul(0.1f, 0.1f, 0.1f, 1f), cor.cpy().mul(0.3f, 0.3f, 0.3f, 1f), esquerda);
            estilo.over = botaoRedondo(cor.cpy().mul(0.32f, 0.32f, 0.32f, 1f),
                cor.cpy().mul(0.16f, 0.16f, 0.16f, 1f), cor.cpy().mul(0.6f, 0.6f, 0.6f, 1f), esquerda);
            estilo.down = botaoRedondo(cor.cpy().mul(0.45f, 0.45f, 0.45f, 1f),
                cor.cpy().mul(0.22f, 0.22f, 0.22f, 1f), cor, esquerda);
            estilo.checked = botaoRedondo(cor.cpy().mul(0.4f, 0.4f, 0.4f, 1f),
                cor.cpy().mul(0.2f, 0.2f, 0.2f, 1f), cor, esquerda);
            estilo.checkedOver = estilo.checked;

            Button botao = new Button(estilo);
            Image icone = new Image(new TextureRegionDrawable(atlas.findRegion(secao[1])));
            icone.setScaling(Scaling.fit);
            // Centraliza o icone no circulo (a aba fica do lado do painel).
            botao.add(icone).size(BOTAO_SIZE * 0.55f)
                .padLeft(esquerda ? 0f : BOTAO_ABA).padRight(esquerda ? BOTAO_ABA : 0f);
            botao.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    acionarBotao(nome);
                }
            });
            botoes.put(nome, botao);
            coluna.add(botao).width(COLUNA_LARGURA).height(BOTAO_SIZE)
                .padBottom(i < secoes.length - 1 ? espaco : 0f).row();
        }
        return coluna;
    }

    /** Circulo com uma aba reta do lado do painel: borda, anel e miolo mais escuro. */
    private static TextureRegionDrawable botaoRedondo(Color anel, Color miolo, Color borda, boolean esquerda) {
        int altura = 128;
        int aba = Math.round(altura * BOTAO_ABA / BOTAO_SIZE);
        int largura = altura + aba;
        int raio = altura / 2;
        int centroX = esquerda ? raio : largura - raio;
        int abaX = esquerda ? raio : 0;
        int abaLargura = largura - raio;
        int espessura = 3;
        Pixmap pm = new Pixmap(largura, altura, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        pm.setColor(borda);
        pm.fillCircle(centroX, raio, raio - 1);
        pm.fillRectangle(abaX, 0, abaLargura, altura);
        pm.setColor(anel);
        pm.fillCircle(centroX, raio, raio - 1 - espessura);
        pm.fillRectangle(esquerda ? abaX : 0, espessura, abaLargura, altura - 2 * espessura);
        pm.setColor(miolo);
        pm.fillCircle(centroX, raio, Math.round(raio * 0.8f));
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return new TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(tex));
    }

    private void construirPaginaBag(Skin skin) {
        bagPage.defaults().top();
        inventoryGrid.top().left();

        Table detalhes = new Table();
        detalhes.setBackground(skin.getDrawable("bag-detail"));
        detalhes.top().left().pad(8);
        detalhes.add(itemName).growX().left().padBottom(10).row();
        detalhes.add(itemQuantity).growX().left().padBottom(5).row();
        detalhes.add(itemFavorite).growX().left().padBottom(5).row();
        detalhes.add(new Table()).growY();

        actionButtons = new Table();
        actionButtons.center();
        favoriteButton = criarBotaoAcao("ui/Star", () -> alternarFavorito());
        trashButton = criarBotaoAcao("ui/Trash", () -> iniciarExclusao());
        actionButtons.add(favoriteButton).size(48).padRight(5);
        actionButtons.add(trashButton).size(48);
        deleteConfirmation = new Table();
        deleteConfirmation.center();
        Button confirmar = criarBotaoAcao("ui/Confirm", () -> confirmarExclusao());
        Button cancelar = criarBotaoAcao("ui/Negate", () -> cancelarExclusao());
        deleteConfirmation.add(confirmar).size(42).padRight(5);
        deleteConfirmation.add(cancelar).size(42);
        deleteConfirmation.setVisible(false);

        actionBar = new Table();
        actionBar.center();
        actionBar.add(actionStatus).growX().center().height(14).padBottom(3).row();
        actionBar.add(actionButtons).center().height(48).row();
        actionBar.add(deleteConfirmation).center().height(42);

        Table painelGrade = new Table();
        painelGrade.pad(7);
        ScrollPane scroll = new ScrollPane(inventoryGrid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setFlickScroll(true);
        painelGrade.add(scroll).grow();

        Table colunaDireita = new Table();
        colunaDireita.add(painelGrade).grow().row();
        capacityLabel.setText("Capacity: 0 / 100");
        capacityLabel.setColor(new Color(0.72f, 0.72f, 0.72f, 1f));
        colunaDireita.add(capacityLabel).center().padTop(5).padBottom(5).row();

        Table moedas = new Table();
        moedas.pad(5, 4, 5, 4);
        String[] tiposMoeda = {"Copper", "Silver", "Gold", "Platinum"};
        for (String tipo : tiposMoeda) {
            Table moeda = new Table();
            Image icone = new Image(new TextureRegionDrawable(atlas.findRegion("ui/currency/" + tipo)));
            icone.setScaling(Scaling.fit);
            Label quantidade = new Label("0", skin, "hud");
            quantidade.setColor(corMoeda(tipo));
            moeda.add(icone).size(18).padRight(2);
            moeda.add(quantidade).minWidth(28).left();
            moedas.add(moeda).expandX().center();
            currencyLabels.put(tipo, quantidade);
        }
        colunaDireita.add(moedas).growX().height(32);

        Table colunaEsquerda = new Table();
        colunaEsquerda.top();
        colunaEsquerda.add(detalhes).growX().growY().row();
        colunaEsquerda.add(actionBar).growX().height(68).padTop(6);

        bagPage.add(colunaEsquerda).width(138).growY().padTop(7).padRight(7);
        bagPage.add(colunaDireita).grow();
        atualizarDetalhes(null);
        atualizarGrade();
    }

    // ===================== EQUIP =====================

    private static final class EquipStats {
        final String nome, tipo;
        final int reqLevel;
        final String reqClass;
        final int bonusDamage, defense, stamina, mana, fourthStatValue;
        final String fourthStatType;

        EquipStats(String nome, String tipo, int reqLevel, String reqClass, int bonusDamage, int defense,
                   int stamina, int mana, String fourthStatType, int fourthStatValue) {
            this.nome = nome;
            this.tipo = tipo;
            this.reqLevel = reqLevel;
            this.reqClass = reqClass;
            this.bonusDamage = bonusDamage;
            this.defense = defense;
            this.stamina = stamina;
            this.mana = mana;
            this.fourthStatType = fourthStatType;
            this.fourthStatValue = fourthStatValue;
        }
    }

    private static final Map<String, EquipStats> ITEM_STATS = new LinkedHashMap<>();
    static {
        ITEM_STATS.put("res://sprites/items/Sword.tres", new EquipStats("Sword", "Sword", 0, "Knight", 100, 0, 0, 0, "None", 0));
        ITEM_STATS.put("res://sprites/items/Bard/Weapons/StarterFlute.tres", new EquipStats("Basic Flute", "Flute", 0, "Bard", 1, 0, 5, 5, "Musicality", 3));
        ITEM_STATS.put("res://sprites/items/Bard/SecondHand/StarterSheet.tres", new EquipStats("Basic Music Sheet", "Music Sheet", 0, "Bard", 0, 0, 10, 10, "Musicality", 3));
        ITEM_STATS.put("res://sprites/items/Knight/Weapons/StarterSword.tres", new EquipStats("Basic Sword", "Sword", 0, "Knight", 1, 5, 10, 0, "Melee", 5));
        ITEM_STATS.put("res://sprites/items/Knight/SecondHand/StarterShield.tres", new EquipStats("Basic Shield", "Shield", 0, "Knight", 1, 5, 10, 0, "", 0));
        ITEM_STATS.put("res://sprites/items/Mage/Weapons/StarterStaff.tres", new EquipStats("Basic Staff", "Staff", 0, "Mage", 1, 0, 0, 10, "Magic", 2));
        ITEM_STATS.put("res://sprites/items/Mage/SecondHand/StarterBook.tres", new EquipStats("Basic Book", "Book", 0, "Mage", 0, 0, 0, 10, "Magic", 5));
        ITEM_STATS.put("res://sprites/items/Ranger/Weapons/StarterBow.tres", new EquipStats("Basic Bow", "Bow", 0, "Ranger", 1, 0, 5, 0, "Focus", 5));
        ITEM_STATS.put("res://sprites/items/Ranger/SecondHand/StarterArrow.tres", new EquipStats("Basic Arrow", "Arrow", 0, "Ranger", 0, 0, 0, 0, "Focus", 5));
    }

    private static String regiaoSlotVazio(String slot) {
        switch (slot) {
            case "Helm": return "ui/slots/HelmSlot";
            case "Necklace": return "ui/slots/NeckSlot";
            case "MainHand": return "ui/slots/SwordSlot";
            case "Hand": return "ui/slots/ShieldSlot";
            case "Chest": return "ui/slots/ChestSlot";
            case "Gloves": return "ui/slots/GlovesSlot";
            case "Boots": return "ui/slots/BootsSlot";
            case "Ring": return "ui/slots/RingSlot";
            default: return null;
        }
    }

    /** Slot onde o item entra, deduzido do caminho (null = nao e' equipavel/desconhecido). */
    private static String slotDoItem(String caminho) {
        if (caminho == null) return null;
        if (caminho.contains("/Weapons/") || caminho.endsWith("/Sword.tres")) return "MainHand";
        if (caminho.contains("/SecondHand/")) return "Hand";
        String nome = caminho.substring(caminho.lastIndexOf('/') + 1).toLowerCase();
        if (nome.contains("helm") || nome.contains("hat")) return "Helm";
        if (nome.contains("neck") || nome.contains("amulet")) return "Necklace";
        if (nome.contains("chest") || nome.contains("armor") || nome.contains("robe")) return "Chest";
        if (nome.contains("glove")) return "Gloves";
        if (nome.contains("boot")) return "Boots";
        if (nome.contains("ring")) return "Ring";
        return null;
    }

    private void construirPaginaEquip(Skin skin) {
        skin.add("equip-slot", UiSkin.retangulo(
            new Color(0.15f, 0.15f, 0.15f, 1f), new Color(0.21f, 0.21f, 0.21f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("equip-slot-hover", UiSkin.retangulo(
            new Color(0.18f, 0.18f, 0.18f, 1f), new Color(0.32f, 0.32f, 0.32f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);
        skin.add("equip-slot-selected", UiSkin.retangulo(
            new Color(0.2f, 0.2f, 0.2f, 1f), new Color(0.95f, 0.65f, 0.24f, 1f), 2),
            com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);

        // Coluna esquerda: grade de slots equipados + detalhes do slot selecionado.
        Table slots = new Table();
        slots.top().left();
        equipSlotsTabela = slots;
        for (String[] linha : SLOTS_EQUIP) {
            for (String nomeSlot : linha) {
                if (nomeSlot == null) {
                    slots.add().size(SLOT_EQUIP).pad(1.5f);
                    continue;
                }
                Button botao = novoBotaoSlotEquip();
                botao.addListener(new ChangeListener() {
                    @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                        selecionarSlotEquip(nomeSlot);
                    }
                });
                equipSlotButtons.put(nomeSlot, botao);
                slots.add(botao).size(SLOT_EQUIP).pad(1.5f);
            }
            slots.row();
        }

        equipColunaEsquerda = new Table();
        equipColunaEsquerda.top().left();
        equipColunaEsquerda.setBackground(UiSkin.retangulo(
            new Color(0.08f, 0.08f, 0.08f, 1f), new Color(0.35f, 0.35f, 0.35f, 1f), 1));
        equipDetalhesEsquerda.top().left();

        // Coluna direita: itens da bag que podem ser equipados + botao + detalhes.
        equipItemGrid.top().left();
        ScrollPane scroll = new ScrollPane(equipItemGrid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        scroll.setFlickScroll(true);
        equipPainelGrade = new Table();
        equipPainelGrade.top().left();
        equipPainelGrade.add(scroll).grow().pad(12, 12, 6, 12);
        equipDetalhesDireita.top().left();

        TextButton.TextButtonStyle estiloEquipar = new TextButton.TextButtonStyle(
            skin.get("verde", TextButton.TextButtonStyle.class));
        estiloEquipar.font = skin.getFont("botao-pequeno-font");
        TextButton.TextButtonStyle estiloDesequipar = new TextButton.TextButtonStyle(
            skin.get("default", TextButton.TextButtonStyle.class));
        estiloDesequipar.font = skin.getFont("botao-pequeno-font");
        equipButton = new TextButton("Equip", estiloEquipar);
        equipButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                equiparSelecionado();
            }
        });
        unequipButton = new TextButton("Unequip", estiloDesequipar);
        unequipButton.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                desequiparSlotSelecionado();
            }
        });

        equipColunaDireita = new Table();
        equipColunaDireita.top().left();
        equipColunaDireita.setBackground(UiSkin.retangulo(
            new Color(0.165f, 0.165f, 0.165f, 1f), new Color(0.165f, 0.165f, 0.165f, 1f), 1));

        equipPage.add(equipColunaEsquerda).width(COLUNA_EQUIP).growY();
        equipPage.add(equipColunaDireita).grow();
        atualizarGradeEquip();
        atualizarDetalhesEquip();
    }

    /** Botao de slot da aba Equip. setChecked() feito pelo codigo NAO dispara
     *  ChangeListener (senao o listener chama a atualizacao de novo, em loop). */
    private Button novoBotaoSlotEquip() {
        Button novo = new Button(estiloSlotEquip());
        novo.setProgrammaticChangeEvents(false);
        return novo;
    }

    private Button.ButtonStyle estiloSlotEquip() {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = skin.getDrawable("equip-slot");
        estilo.over = skin.getDrawable("equip-slot-hover");
        estilo.down = skin.getDrawable("equip-slot-selected");
        estilo.checked = skin.getDrawable("equip-slot-selected");
        estilo.checkedOver = estilo.checked;
        return estilo;
    }

    private void selecionarSlotEquip(String slot) {
        selectedSlot = slot.equals(selectedSlot) ? "" : slot;
        selectedEquipCandidate = -1;
        atualizarGradeEquip();
        atualizarDetalhesEquip();
    }

    private void atualizarGradeEquip() {
        equipCandidatos.clear();
        for (int i = 0; i < inventoryItems.size(); i++) {
            String caminho = inventoryItems.get(i).itemPath;
            String slotItem = slotDoItem(caminho);
            if (slotItem == null && !ITEM_STATS.containsKey(caminho)) continue;
            // Filtro: com um slot selecionado, so' mostra o que entra nele.
            if (!selectedSlot.isEmpty() && !selectedSlot.equals(slotItem)) continue;
            equipCandidatos.add(i);
        }
        equipItemGrid.clearChildren();
        if (equipCandidatos.isEmpty()) {
            Button vazio = novoBotaoSlotEquip();
            vazio.setDisabled(true);
            equipItemGrid.add(vazio).size(SLOT_EQUIP).pad(1.5f);
            return;
        }
        for (int n = 0; n < equipCandidatos.size(); n++) {
            final int indice = equipCandidatos.get(n);
            InventoryItem item = inventoryItems.get(indice);
            Button slotBtn = novoBotaoSlotEquip();
            slotBtn.setChecked(indice == selectedEquipCandidate);
            TextureAtlas.AtlasRegion textura = iconeDoItem(item.itemPath);
            if (textura != null) {
                Image icone = new Image(new TextureRegionDrawable(textura));
                icone.setScaling(Scaling.fit);
                slotBtn.add(icone).grow().pad(6);
            }
            slotBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    selectedEquipCandidate = (selectedEquipCandidate == indice) ? -1 : indice;
                    atualizarGradeEquip();
                    atualizarDetalhesEquip();
                }
            });
            equipItemGrid.add(slotBtn).size(SLOT_EQUIP).pad(1.5f);
            if ((n + 1) % COLUNAS_EQUIP == 0) equipItemGrid.row();
        }
    }

    private void atualizarDetalhesEquip() {
        for (Map.Entry<String, Button> entrada : equipSlotButtons.entrySet()) {
            entrada.getValue().setChecked(entrada.getKey().equals(selectedSlot));
        }
        String equipadoPath = selectedSlot.isEmpty() ? null : equippedItemPaths.get(selectedSlot);
        String candidatoPath = selectedEquipCandidate >= 0 && selectedEquipCandidate < inventoryItems.size()
            ? inventoryItems.get(selectedEquipCandidate).itemPath : null;
        if (candidatoPath == null || !equipCandidatos.contains(selectedEquipCandidate)) {
            candidatoPath = null;
            selectedEquipCandidate = -1;
        }

        // Esquerda: detalhes do item equipado no slot selecionado.
        equipColunaEsquerda.clearChildren();
        equipColunaEsquerda.add(equipSlotsTabela).top().left().pad(4).row();
        if (equipadoPath != null && candidatoPath == null) {
            preencherBlocoStats(equipDetalhesEsquerda, equipadoPath, null);
            equipColunaEsquerda.add(separadorEquip()).growX().height(1).padTop(4).row();
            equipColunaEsquerda.add(equipDetalhesEsquerda).growX().top().left().pad(6, 7, 6, 7).row();
        }

        // Direita: grade; com candidato -> botao Equip + detalhes comparando
        // com o que esta equipado no slot dele; com slot equipado -> Unequip.
        equipColunaDireita.clearChildren();
        if (candidatoPath != null) {
            String slotAlvo = slotDoItem(candidatoPath);
            String atual = slotAlvo == null ? null : equippedItemPaths.get(slotAlvo);
            equipColunaDireita.add(equipPainelGrade).growX().height(ALTURA_GRADE_EQUIP - 54f).row();
            equipColunaDireita.add(equipButton).width(120).height(42).right().padRight(14).padBottom(8).row();
            equipColunaDireita.add(separadorEquip()).growX().height(1).row();
            preencherBlocoStats(equipDetalhesDireita, candidatoPath, atual == null ? "" : atual);
            equipColunaDireita.add(equipDetalhesDireita).grow().top().left().pad(6, 7, 6, 7);
            equipButton.setDisabled(slotAlvo == null && selectedSlot.isEmpty());
        } else {
            equipColunaDireita.add(equipPainelGrade).grow().row();
            if (equipadoPath != null) {
                equipColunaDireita.add(unequipButton).width(120).height(42).right().padRight(14).padBottom(10);
            }
        }
    }

    private Image separadorEquip() {
        return criarPreenchimentoBarra(new Color(0.35f, 0.35f, 0.35f, 1f));
    }

    private static Color corClasse(String classe) {
        if (classe == null) return Color.LIGHT_GRAY;
        switch (classe) {
            case "Knight": return Color.WHITE;
            case "Ranger": return Color.valueOf("4caf50");
            case "Mage": return Color.valueOf("a474d4");
            case "Bard": return Color.valueOf("ffd54f");
            default: return Color.LIGHT_GRAY;
        }
    }

    private static Color corQuartoStat(String tipo) {
        if (tipo == null) return Color.WHITE;
        switch (tipo) {
            case "Magic": return Color.valueOf("a474d4");
            case "Focus": return Color.valueOf("00d084");
            case "Musicality": return Color.valueOf("f0a35a");
            case "Melee": return Color.valueOf("cccccc");
            default: return Color.WHITE;
        }
    }

    private static final Color COR_REQUISITO = Color.valueOf("ffff33");
    private static final Color COR_STAMINA = Color.valueOf("22e022");
    private static final Color COR_MANA = Color.valueOf("4a8cff");

    /**
     * Nome + requisitos + stats do item. compararCom != null mostra a diferenca
     * pro item equipado no mesmo slot ("" = slot vazio), ex: "Mana 10 (+10)".
     */
    private void preencherBlocoStats(Table bloco, String itemPath, String compararCom) {
        bloco.clearChildren();
        bloco.top().left();
        // Fonte "default" (com contorno preto) reduzida, linhas com altura fixa
        // pra ficarem coladas.
        Label nome = new Label(nomeExibicao(itemPath), skin, "default");
        nome.setFontScale(0.75f);
        bloco.add(nome).left().height(ALTURA_LINHA_STAT + 3f).padBottom(1).row();
        EquipStats dados = ITEM_STATS.get(itemPath);
        if (dados == null) return;
        EquipStats base = compararCom == null ? null : ITEM_STATS.get(compararCom);
        boolean comparar = compararCom != null;
        bloco.add(linhaStat("Req. Lv " + dados.reqLevel, COR_REQUISITO)).left().height(ALTURA_LINHA_STAT).row();
        // Vermelho = classe errada (o servidor pune quem equipa item de outra classe).
        boolean classeCerta = "All".equals(dados.reqClass) || classeJogador.equals(dados.reqClass);
        bloco.add(linhaStat(dados.reqClass + " " + dados.tipo,
            classeCerta ? COR_REQUISITO : Color.valueOf("ff4a4a"))).left().height(ALTURA_LINHA_STAT).row();
        adicionarLinhaStat(bloco, "Attack", dados.bonusDamage, base == null ? 0 : base.bonusDamage, comparar, Color.WHITE);
        adicionarLinhaStat(bloco, "Defense", dados.defense, base == null ? 0 : base.defense, comparar, Color.WHITE);
        adicionarLinhaStat(bloco, "Stamina", dados.stamina, base == null ? 0 : base.stamina, comparar, COR_STAMINA);
        adicionarLinhaStat(bloco, "Mana", dados.mana, base == null ? 0 : base.mana, comparar, COR_MANA);
        if (dados.fourthStatType != null && !dados.fourthStatType.isEmpty() && !"None".equals(dados.fourthStatType)) {
            int anterior = base != null && dados.fourthStatType.equals(base.fourthStatType) ? base.fourthStatValue : 0;
            adicionarLinhaStat(bloco, dados.fourthStatType, dados.fourthStatValue, anterior, comparar,
                corQuartoStat(dados.fourthStatType));
        }
    }

    private void adicionarLinhaStat(Table bloco, String nome, int valor, int anterior, boolean comparar, Color cor) {
        if (valor == 0 && (!comparar || anterior == 0)) return;
        String texto = nome + " " + valor;
        if (comparar) {
            int diferenca = valor - anterior;
            texto += " (" + (diferenca >= 0 ? "+" : "") + diferenca + ")";
        }
        bloco.add(linhaStat(texto, cor)).left().height(ALTURA_LINHA_STAT).row();
    }

    private static final float ALTURA_LINHA_STAT = 14f;

    private Label linhaStat(String texto, Color cor) {
        Label label = new Label(texto, skin, "default");
        label.setFontScale(0.62f);
        label.setColor(cor);
        return label;
    }

    private void equiparSelecionado() {
        if (selectedEquipCandidate < 0 || selectedEquipCandidate >= inventoryItems.size()
            || !socket.isConnected()) return;
        InventoryItem item = inventoryItems.get(selectedEquipCandidate);
        String slotItem = slotDoItem(item.itemPath);
        String slot = slotItem != null ? slotItem : selectedSlot;
        if (item.instanceId.isEmpty() || slot.isEmpty()) return;
        String instanceId = item.instanceId;
        String payload = GameSocket.obj(w -> { w.set("instance_id", instanceId); w.set("slot", slot); });
        socket.emitRaw("equip_item", payload);
        selectedEquipCandidate = -1;
        selectedSlot = slot;
    }

    private void desequiparSlotSelecionado() {
        if (selectedSlot.isEmpty() || !socket.isConnected()) return;
        String slot = selectedSlot;
        String payload = GameSocket.obj(w -> w.set("slot", slot));
        socket.emitRaw("unequip_item", payload);
    }

    public void atualizarEquipados(JsonValue dados) {
        equippedItemPaths.clear();
        if (dados != null && dados.isObject()) {
            for (JsonValue entrada = dados.child; entrada != null; entrada = entrada.next) {
                String caminho = entrada.getString("item", "");
                if (!caminho.isEmpty()) equippedItemPaths.put(entrada.name, caminho);
            }
        }
        for (Map.Entry<String, Button> entrada : equipSlotButtons.entrySet()) {
            Button botao = entrada.getValue();
            botao.clearChildren();
            String caminho = equippedItemPaths.get(entrada.getKey());
            TextureAtlas.AtlasRegion textura = caminho == null ? null : iconeDoItem(caminho);
            if (textura == null) textura = atlas.findRegion(regiaoSlotVazio(entrada.getKey()));
            if (textura != null) {
                Image icone = new Image(new TextureRegionDrawable(textura));
                icone.setScaling(Scaling.fit);
                botao.add(icone).grow().pad(caminho == null ? 4 : 6);
            }
        }
        atualizarDetalhesEquip();
    }

    // ===================== SKILLS =====================

    private String[] skillPrincipal() {
        switch (classeJogador) {
            case "Ranger": return new String[]{"distance", "Distance"};
            case "Mage": return new String[]{"magic", "Magic"};
            case "Bard": return new String[]{"musicality", "Musicality"};
            default: return new String[]{"melee", "Melee"};
        }
    }

    private String iconeSkillPrincipal() {
        switch (classeJogador) {
            case "Ranger": return "ui/FocusIcon";
            case "Mage": return "ui/items/StarterStaff";
            case "Bard": return "ui/MusicalityIcon";
            // No .atlas a regiao esta grafada "MeeleIcon".
            default: return atlas.findRegion("ui/MeeleIcon") != null ? "ui/MeeleIcon" : "ui/MeleeIcon";
        }
    }

    private static final class BarraSkill {
        final Label nivelLabel;
        final Label percentLabel;
        final Image preenchimento;
        final Table alinhador;
        final boolean casasDecimais;

        BarraSkill(Label nivelLabel, Label percentLabel, Image preenchimento, Table alinhador, boolean casasDecimais) {
            this.nivelLabel = nivelLabel;
            this.percentLabel = percentLabel;
            this.preenchimento = preenchimento;
            this.alinhador = alinhador;
            this.casasDecimais = casasDecimais;
        }
    }

    /** Uma linha do topo: [nome][nivel] .... [icone][barra com %]. */
    private BarraSkill criarBarraSkill(Table pai, String iconRegion, String nome, Color corTexto,
                                       Color corBarra, Color corPercentual, boolean comNivel) {
        Label nomeLabel = new Label(nome, skin, "hud");
        nomeLabel.setColor(corTexto);
        Label nivelLabel = null;
        pai.add(nomeLabel).left().width(LARGURA_NOME_SKILL);
        if (comNivel) {
            nivelLabel = new Label("1", skin, "hud");
            nivelLabel.setColor(corTexto);
            pai.add(nivelLabel).left().expandX();
        } else {
            pai.add().expandX();
        }

        TextureAtlas.AtlasRegion region = iconRegion == null ? null : atlas.findRegion(iconRegion);
        if (region != null) {
            Image icone = new Image(new TextureRegionDrawable(region));
            icone.setScaling(Scaling.fit);
            pai.add(icone).size(TAMANHO_ICONE_SKILL).padRight(8);
        } else {
            pai.add().size(TAMANHO_ICONE_SKILL).padRight(8);
        }

        Stack pilha = new Stack();
        Table fundo = new Table();
        fundo.setBackground(UiSkin.retangulo(new Color(0.055f, 0.055f, 0.055f, 1f),
            new Color(0.2f, 0.2f, 0.2f, 1f), 1));
        pilha.add(fundo);
        Table alinhador = new Table();
        alinhador.left().pad(1);
        Image preenchimento = criarPreenchimentoBarra(corBarra);
        alinhador.add(preenchimento).size(0f, ALTURA_BARRA_SKILL - 2f);
        pilha.add(alinhador);
        Label percentLabel = new Label("0%", skin, "hud");
        percentLabel.setColor(corPercentual);
        percentLabel.setAlignment(Align.center);
        pilha.add(percentLabel);
        pai.add(pilha).width(LARGURA_BARRA_SKILL).height(ALTURA_BARRA_SKILL).row();

        return new BarraSkill(nivelLabel, percentLabel, preenchimento, alinhador, comNivel);
    }

    private static final Color COR_NIVEL = Color.valueOf("ffeb3b");
    private static final Color COR_BARRA_XP = Color.valueOf("f5b82e");
    private static final Color COR_DEFESA = Color.valueOf("6ab7ff");
    private static final Color COR_FOME = Color.valueOf("f0a35a");
    private static final Color COR_BARRA_FOME = Color.valueOf("8a4b2a");

    private static Color corSkillPrincipal(String chaveServidor) {
        if (chaveServidor == null) return Color.WHITE;
        switch (chaveServidor) {
            case "magic": return Color.valueOf("a474d4");
            case "distance": return Color.valueOf("00d084");
            case "musicality": return Color.valueOf("ffa24e");
            case "melee": return Color.valueOf("cccccc");
            default: return Color.WHITE;
        }
    }

    private void construirPaginaSkills(Skin skin) {
        skillsPage.top().left();
        String[] principal = skillPrincipal();
        Color corPrincipal = corSkillPrincipal(principal[0]);

        Table barras = new Table();
        barras.top().left();
        // Espaco entre linhas em TODAS as celulas - se ficar so' na barra,
        // icone/nome centralizam numa linha mais alta e descem em relacao a ela.
        barras.defaults().padBottom(10);
        barraNivel = criarBarraSkill(barras, "ui/XPIcon", "Level", COR_NIVEL, COR_BARRA_XP, Color.WHITE, true);
        barraPrincipal = criarBarraSkill(barras, iconeSkillPrincipal(), principal[1], corPrincipal, corPrincipal, Color.WHITE, true);
        barraDefesa = criarBarraSkill(barras, "ui/DefenseIcon", "Defense", COR_DEFESA, COR_DEFESA, Color.WHITE, true);
        barraFome = criarBarraSkill(barras, "ui/HungerIcon", "Fullness", COR_FOME, COR_BARRA_FOME, COR_FOME, false);

        Image separador = criarPreenchimentoBarra(new Color(0.42f, 0.42f, 0.42f, 1f));

        Table statusEsquerda = new Table();
        statusEsquerda.top().left();
        statusEsquerda.add(linhaStatus("ui/ClassIcon", "Class", "class")).left().row();
        statusEsquerda.add(linhaStatus("ui/XPIcon", "Experience", "exp")).left().row();
        statusEsquerda.add(linhaStatus("ui/NextLevelIcon", "Next Level", "next")).left().row();
        statusEsquerda.add(linhaStatus("ui/CritIcon", "Crit Chance", "crit")).left().row();
        statusEsquerda.add(linhaStatus("ui/BlockIcon", "Block Chance", "block")).left().row();
        statusEsquerda.add(linhaStatus("ui/currency/BagIcon", "Capacity", "capacity")).left().row();

        Table statusDireita = new Table();
        statusDireita.top().left();
        statusDireita.add(linhaStatus("ui/TotalXP", "Total EXP", "total_exp")).left().row();
        statusDireita.add(linhaStatus("ui/TotalKills", "Total Kills", "total_kills")).left().row();

        Table status = new Table();
        status.top().left();
        status.add(statusEsquerda).top().left().expandX().fillX();
        status.add(statusDireita).top().left().expandX().fillX();

        skillsPage.add(barras).growX().height(ALTURA_TOPO_SKILLS).top().left().padTop(4).row();
        skillsPage.add(separador).growX().height(1).padBottom(12).row();
        skillsPage.add(status).growX().top().left().padLeft(4).row();
        skillsPage.add().grow();
        atualizarSkills(null, 1, 0, 0);
    }

    private Image criarPreenchimentoBarra(Color cor) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Image imagem = new Image(new TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(
            new Texture(pixmap))));
        pixmap.dispose();
        imagem.setColor(cor);
        return imagem;
    }

    private void atualizarBarra(BarraSkill barra, int nivel, float percentual) {
        float pct = Math.max(0f, Math.min(1f, percentual));
        if (barra.nivelLabel != null) barra.nivelLabel.setText(String.valueOf(nivel));
        barra.percentLabel.setText(barra.casasDecimais
            ? String.format(java.util.Locale.US, "%.2f%%", pct * 100f)
            : String.format(java.util.Locale.US, "%.0f%%", pct * 100f));
        barra.alinhador.getCell(barra.preenchimento).width((LARGURA_BARRA_SKILL - 2f) * pct);
        barra.alinhador.invalidateHierarchy();
    }

    private static int getHitsToLevel(int level, float multiplicador) {
        double L = level - 3;
        return (int) Math.ceil((L * Math.pow(1.0825, L) + (1.0825 * L) + 30.0) * multiplicador);
    }

    private static float getSkillMultiplier(String classe, boolean defesa) {
        if (defesa) {
            if ("Knight".equals(classe)) return 0.8f;
            if ("Ranger".equals(classe)) return 1.0f;
            if ("Mage".equals(classe) || "Bard".equals(classe)) return 1.2f;
        } else {
            if ("Knight".equals(classe)) return 1.2f;
            if ("Ranger".equals(classe)) return 1.0f;
            if ("Mage".equals(classe) || "Bard".equals(classe)) return 0.8f;
        }
        return 1.0f;
    }

    private static int getExpForLevel(int level) {
        if (level <= 1) return 0;
        return (int) ((50.0 / 3.0) * (Math.pow(level, 3) - 6 * Math.pow(level, 2) + 17 * level - 12));
    }

    private static String formatarNumero(long valor) {
        return String.format(java.util.Locale.US, "%,d", valor).replace(',', '.');
    }

    private static String formatarPercentual(float valor) {
        return (valor == Math.floor(valor)
            ? String.valueOf((int) valor)
            : String.format(java.util.Locale.US, "%.1f", valor)) + "%";
    }

    /**
     * Campos opcionais lidos de "skills" (o servidor ainda nao manda todos):
     * fullness/hunger (0-100), crit_chance, block_chance e total_exp.
     */
    public void atualizarSkills(JsonValue skills, int level, int exp, int kills) {
        this.ultimasSkills = skills;
        this.nivelAtual = level;
        this.expAtual = exp;
        this.killsAtual = kills;

        int expAtualNivel = getExpForLevel(level);
        int expProximo = getExpForLevel(level + 1);
        float pctNivel = expProximo > expAtualNivel ? (float) (exp - expAtualNivel) / (expProximo - expAtualNivel) : 0f;
        atualizarBarra(barraNivel, level, pctNivel);

        String[] principal = skillPrincipal();
        int nivelPrincipal = skills != null ? skills.getInt(principal[0], 10) : 10;
        int hitsPrincipal = skills != null ? skills.getInt(principal[0] + "_hits", 0) : 0;
        int necessarioPrincipal = getHitsToLevel(nivelPrincipal + 1, getSkillMultiplier(classeJogador, false));
        float pctPrincipal = necessarioPrincipal > 0 ? (float) hitsPrincipal / necessarioPrincipal : 0f;
        atualizarBarra(barraPrincipal, nivelPrincipal, pctPrincipal);

        int nivelDefesa = skills != null ? skills.getInt("defense", 10) : 10;
        int hitsDefesa = skills != null ? skills.getInt("defense_hits", 0) : 0;
        int necessarioDefesa = getHitsToLevel(nivelDefesa + 1, getSkillMultiplier(classeJogador, true));
        float pctDefesa = necessarioDefesa > 0 ? (float) hitsDefesa / necessarioDefesa : 0f;
        atualizarBarra(barraDefesa, nivelDefesa, pctDefesa);

        float fullness = skills != null ? skills.getFloat("fullness", skills.getFloat("hunger", 0f)) : 0f;
        atualizarBarra(barraFome, 0, fullness / 100f);

        float crit = skills != null ? skills.getFloat("crit_chance", 10f) : 10f;
        float block = skills != null ? skills.getFloat("block_chance", 10f) : 10f;
        long totalExp = skills != null ? skills.getLong("total_exp", exp) : exp;

        valoresStatus.get("class").setText(classeJogador);
        valoresStatus.get("exp").setText(formatarNumero(exp));
        valoresStatus.get("next").setText(formatarNumero(expProximo));
        valoresStatus.get("crit").setText(formatarPercentual(crit));
        valoresStatus.get("block").setText(formatarPercentual(block));
        atualizarTextoCapacidade();
        valoresStatus.get("total_exp").setText(formatarNumero(totalExp));
        valoresStatus.get("total_kills").setText(formatarNumero(kills));
    }

    private void atualizarTextoCapacidade() {
        Label capacidade = valoresStatus.get("capacity");
        if (capacidade != null) {
            capacidade.setText(String.format(java.util.Locale.US, "%.0f/%.0f", capacityUsed, capacityMaximum));
        }
    }

    /** [icone] Rotulo    valor - o valor fica alinhado numa coluna fixa. */
    private Table linhaStatus(String iconRegion, String rotulo, String chave) {
        Table linha = new Table();
        TextureAtlas.AtlasRegion region = iconRegion == null ? null : atlas.findRegion(iconRegion);
        if (region != null) {
            Image icone = new Image(new TextureRegionDrawable(region));
            icone.setScaling(Scaling.fit);
            linha.add(icone).size(TAMANHO_ICONE_STATUS).padRight(8);
        } else {
            linha.add().size(TAMANHO_ICONE_STATUS).padRight(8);
        }
        Label r = new Label(rotulo, skin, "hud");
        Label v = new Label("", skin, "hud");
        linha.add(r).left().width(LARGURA_ROTULO_STATUS);
        linha.add(v).left();
        linha.padBottom(8);
        valoresStatus.put(chave, v);
        return linha;
    }

    private Color corMoeda(String tipo) {
        switch (tipo) {
            case "Copper": return Color.valueOf("ff8747");
            case "Silver": return Color.valueOf("d5d7dc");
            case "Gold": return Color.valueOf("ffd34e");
            case "Platinum": return Color.valueOf("d9e2ef");
            default: return Color.WHITE;
        }
    }

    private void atualizarGrade() {
        inventoryGrid.clearChildren();
        int quantidadeSlots = inventoryItems.size();
        for (int i = 0; i < quantidadeSlots; i++) {
            final int indice = i;
            Button.ButtonStyle estilo = new Button.ButtonStyle();
            estilo.up = skinDrawable("bag-slot");
            estilo.over = skinDrawable("bag-slot-hover");
            estilo.down = skinDrawable("bag-slot-selected");
            estilo.checked = skinDrawable("bag-slot-selected");
            estilo.checkedOver = estilo.checked;
            Button slot = new Button(estilo);
            slot.setChecked(i == selectedItem);
            if (i < inventoryItems.size()) {
                InventoryItem item = inventoryItems.get(i);
                Stack conteudo = new Stack();
                TextureAtlas.AtlasRegion textura = iconeDoItem(item.itemPath);
                if (textura != null) {
                    Image icone = new Image(new TextureRegionDrawable(textura));
                    icone.setScaling(Scaling.fit);
                    conteudo.add(icone);
                }
                if (item.quantity > 1 || item.favorite) {
                    Table marcadores = new Table();
                    marcadores.bottom().right();
                    if (item.favorite) {
                        Label favorito = new Label("F", skin, "hud");
                        favorito.setColor(new Color(1f, 0.82f, 0.24f, 1f));
                        marcadores.add(favorito).left().expandX();
                    }
                    if (item.quantity > 1) {
                        Label quantidade = new Label(String.valueOf(item.quantity), skin, "hud");
                        quantidade.setColor(Color.WHITE);
                        marcadores.add(quantidade).right();
                    }
                    conteudo.add(marcadores);
                }
                slot.add(conteudo).grow().pad(4);
            }
            slot.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (indice >= inventoryItems.size()) {
                        selectedItem = -1;
                        selectedInstanceId = "";
                        atualizarDetalhes(null);
                    } else {
                        selectedItem = indice;
                        selectedInstanceId = inventoryItems.get(indice).instanceId;
                        atualizarDetalhes(inventoryItems.get(indice));
                    }
                    atualizarGrade();
                }
            });
            inventoryGrid.add(slot).size(42).pad(1);
            if ((i + 1) % COLUNAS_INVENTARIO == 0) inventoryGrid.row();
        }
    }

    private com.badlogic.gdx.scenes.scene2d.utils.Drawable skinDrawable(String name) {
        return skin.getDrawable(name);
    }

    private TextureAtlas.AtlasRegion iconeDoItem(String caminhoItem) {
        String nome = caminhoItem == null ? "" : caminhoItem.substring(caminhoItem.lastIndexOf('/') + 1);
        int extensao = nome.lastIndexOf('.');
        if (extensao >= 0) nome = nome.substring(0, extensao);
        String[] tentativas = {nome, nome.equals("StarterSheet") ? "StarterMusicsheet" : ""};
        for (String tentativa : tentativas) {
            if (tentativa.isEmpty()) continue;
            TextureAtlas.AtlasRegion region = atlas.findRegion("ui/items/" + tentativa);
            if (region != null) return region;
        }
        String lower = nome.toLowerCase();
        if (lower.contains("arrow")) return atlas.findRegion("ui/items/StarterArrow");
        if (lower.contains("sword")) return atlas.findRegion("ui/items/StarterSword");
        if (lower.contains("shield")) return atlas.findRegion("ui/items/StarterShield");
        if (lower.contains("bow")) return atlas.findRegion("ui/items/StarterBow");
        if (lower.contains("staff")) return atlas.findRegion("ui/items/StarterStaff");
        if (lower.contains("flute")) return atlas.findRegion("ui/items/StarterFlute");
        if (lower.contains("book")) return atlas.findRegion("ui/items/StarterBook");
        return atlas.findRegion("ui/currency/BagIcon");
    }

    private void atualizarDetalhes(InventoryItem item) {
        if (item == null) {
            itemName.setText("");
            itemQuantity.setText("");
            itemFavorite.setText("");
            actionStatus.setText("");
            favoriteButton.setDisabled(true);
            trashButton.setDisabled(true);
            return;
        }
        itemName.setText(nomeExibicao(item.itemPath));
        itemQuantity.setText(item.quantity > 1 ? "Quantity: " + item.quantity : "");
        itemFavorite.setText(item.favorite ? "Favorite" : "");
        actionStatus.setText("");
        favoriteButton.setDisabled(item.instanceId.isEmpty());
        trashButton.setDisabled(item.instanceId.isEmpty() || item.favorite);
        favoriteButton.setColor(item.favorite ? new Color(1f, 0.82f, 0.24f, 1f) : Color.WHITE);
        cancelarExclusao();
    }

    private Button criarBotaoAcao(String regiao, Runnable acao) {
        Button.ButtonStyle estilo = new Button.ButtonStyle();
        estilo.up = skin.getDrawable("bag-slot");
        estilo.over = skin.getDrawable("bag-slot-hover");
        estilo.down = skin.getDrawable("bag-slot-selected");
        Button botao = new Button(estilo);
        Image icone = new Image(new TextureRegionDrawable(atlas.findRegion(regiao)));
        icone.setScaling(Scaling.fit);
        botao.add(icone).size(24);
        botao.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                acao.run();
            }
        });
        return botao;
    }

    private void alternarFavorito() {
        InventoryItem item = itemSelecionado();
        if (item == null || item.instanceId.isEmpty() || !socket.isConnected()) return;
        String payload = GameSocket.obj(w -> w.set("instance_id", item.instanceId));
        socket.emitRaw("toggle_favorite_item", payload);
    }

    private void iniciarExclusao() {
        InventoryItem item = itemSelecionado();
        if (item == null || item.favorite || item.instanceId.isEmpty()) return;
        confirmandoExclusao = true;
        actionStatus.setText("Delete this item?");
        actionButtons.setVisible(false);
        deleteConfirmation.setVisible(true);
    }

    private void confirmarExclusao() {
        InventoryItem item = itemSelecionado();
        if (!confirmandoExclusao || item == null || item.favorite || item.instanceId.isEmpty() || !socket.isConnected()) return;
        String payload = GameSocket.obj(w -> {
            w.array("instance_ids");
            w.value(item.instanceId);
            w.pop();
        });
        socket.emitRaw("delete_items", payload);
        selectedItem = -1;
        selectedInstanceId = "";
        atualizarDetalhes(null);
        confirmandoExclusao = false;
        actionStatus.setText("Deleting...");
        deleteConfirmation.setVisible(false);
    }

    private void cancelarExclusao() {
        confirmandoExclusao = false;
        if (actionButtons != null) actionButtons.setVisible(true);
        if (deleteConfirmation != null) deleteConfirmation.setVisible(false);
    }

    private InventoryItem itemSelecionado() {
        return selectedItem >= 0 && selectedItem < inventoryItems.size() ? inventoryItems.get(selectedItem) : null;
    }

    private String nomeExibicao(String caminho) {
        if (caminho == null || caminho.isEmpty()) return "Unknown item";
        EquipStats dados = ITEM_STATS.get(caminho);
        if (dados != null) return dados.nome;
        String nome = caminho.substring(caminho.lastIndexOf('/') + 1);
        int extensao = nome.lastIndexOf('.');
        if (extensao >= 0) nome = nome.substring(0, extensao);
        nome = nome.replace("Starter", "").replaceAll("([a-z])([A-Z])", "$1 $2");
        return nome.isEmpty() ? "Item" : nome;
    }

    public void atualizarInventario(JsonValue dados) {
        inventoryItems.clear();
        if (dados != null && dados.isArray()) {
            for (JsonValue entrada = dados.child; entrada != null; entrada = entrada.next) {
                if (!entrada.isObject()) continue;
                String caminho = entrada.getString("item", "");
                if (caminho.isEmpty()) continue;
                inventoryItems.add(new InventoryItem(entrada.getString("id", ""), caminho,
                    Math.max(1, entrada.getInt("qty", 1)), entrada.getBoolean("favorite", false)));
            }
        }
        selectedItem = -1;
        if (!selectedInstanceId.isEmpty()) {
            for (int i = 0; i < inventoryItems.size(); i++) {
                if (selectedInstanceId.equals(inventoryItems.get(i).instanceId)) {
                    selectedItem = i;
                    break;
                }
            }
            if (selectedItem < 0) selectedInstanceId = "";
        }
        atualizarDetalhes(selectedItem >= 0 ? inventoryItems.get(selectedItem) : null);
        atualizarGrade();
        selectedEquipCandidate = -1;
        atualizarGradeEquip();
        atualizarDetalhesEquip();
    }

    public void adicionarItens(JsonValue dados) {
        if (dados == null || !dados.isArray()) return;
        for (JsonValue entrada = dados.child; entrada != null; entrada = entrada.next) {
            if (!entrada.isObject()) continue;
            String caminho = entrada.getString("item", "");
            if (!caminho.isEmpty()) inventoryItems.add(new InventoryItem(entrada.getString("id", ""), caminho,
                Math.max(1, entrada.getInt("qty", 1)), entrada.getBoolean("favorite", false)));
        }
        atualizarGrade();
        atualizarGradeEquip();
    }

    public void atualizarMoedas(long total) {
        currencyTotal = Math.max(0, total);
        long platinum = currencyTotal / 1_000_000L;
        long remainder = currencyTotal % 1_000_000L;
        long gold = remainder / 10_000L;
        remainder %= 10_000L;
        long silver = remainder / 100L;
        long copper = remainder % 100L;
        currencyLabels.get("Copper").setText(String.valueOf(copper));
        currencyLabels.get("Silver").setText(String.valueOf(silver));
        currencyLabels.get("Gold").setText(String.valueOf(gold));
        currencyLabels.get("Platinum").setText(String.valueOf(platinum));
    }

    public void atualizarCapacidade(float usada, float maxima) {
        capacityUsed = usada;
        capacityMaximum = maxima;
        capacityLabel.setText(String.format("Capacity: %.1f / %.0f", usada, maxima));
        atualizarTextoCapacidade();
    }

    private static final class InventoryItem {
        final String instanceId;
        final String itemPath;
        final int quantity;
        final boolean favorite;

        InventoryItem(String instanceId, String itemPath, int quantity, boolean favorite) {
            this.instanceId = instanceId;
            this.itemPath = itemPath;
            this.quantity = quantity;
            this.favorite = favorite;
        }
    }

    private boolean atualizandoSecao = false;

    private void selecionarSecao(String secao) {
        if (atualizandoSecao) return;
        atualizandoSecao = true;
        try {
            secaoAtual = secao;
            tituloSecao.setText(secao);
            boolean comTitulo = !"Bag".equals(secao) && !"Skills".equals(secao) && !"Equip".equals(secao);
            tituloSecao.setVisible(comTitulo);
            mainWindow.clearChildren();
            // Equip ocupa o painel inteiro (colunas encostam na borda).
            mainWindow.pad("Equip".equals(secao) ? 1 : 14);
            if (comTitulo) {
                mainWindow.add(tituloSecao).growX().left().padBottom(9).row();
            }
            Table pagina = "Equip".equals(secao) ? equipPage : "Skills".equals(secao) ? skillsPage : bagPage;
            mainWindow.add(pagina).grow();
            actionBar.setVisible("Bag".equals(secao));
            for (Map.Entry<String, Button> entrada : botoes.entrySet()) {
                entrada.getValue().setChecked(entrada.getKey().equals(secao));
            }
        } finally {
            atualizandoSecao = false;
        }
    }

    private Color corDaSecao(String secao) {
        switch (secao) {
            case "Exit": return Color.valueOf("e53935");
            case "Equip": return Color.valueOf("aeb4bb");
            case "Bag": return Color.valueOf("f28c28");
            case "Skills": return Color.valueOf("f062a6");
            case "Vanity": return Color.valueOf("f2cf32");
            case "Spells": return Color.valueOf("9b59d0");
            case "Party": return Color.valueOf("c49a6c");
            case "Friends": return Color.valueOf("c2185b");
            case "Map": return Color.valueOf("42a85a");
            case "Rank": return Color.valueOf("f5d328");
            default: return Color.WHITE;
        }
    }

    // Atalhos do teclado com o menu aberto: seguem a ordem dos botoes na tela
    // (coluna direita de cima pra baixo, depois a esquerda). 1 = Equip,
    // 2 = Bag, 3 = Skills, ... 9 = Rank, 0 = Exit (Esc tambem fecha, ver WorldScreen).
    private static final String[] ORDEM_ATALHOS = {
        "Equip", "Bag", "Skills", "Vanity", "Spells", "Party", "Friends", "Map", "Rank", "Exit"
    };
    private static final int[][] TECLAS_ATALHOS = {
        {com.badlogic.gdx.Input.Keys.NUM_1, com.badlogic.gdx.Input.Keys.NUMPAD_1},
        {com.badlogic.gdx.Input.Keys.NUM_2, com.badlogic.gdx.Input.Keys.NUMPAD_2},
        {com.badlogic.gdx.Input.Keys.NUM_3, com.badlogic.gdx.Input.Keys.NUMPAD_3},
        {com.badlogic.gdx.Input.Keys.NUM_4, com.badlogic.gdx.Input.Keys.NUMPAD_4},
        {com.badlogic.gdx.Input.Keys.NUM_5, com.badlogic.gdx.Input.Keys.NUMPAD_5},
        {com.badlogic.gdx.Input.Keys.NUM_6, com.badlogic.gdx.Input.Keys.NUMPAD_6},
        {com.badlogic.gdx.Input.Keys.NUM_7, com.badlogic.gdx.Input.Keys.NUMPAD_7},
        {com.badlogic.gdx.Input.Keys.NUM_8, com.badlogic.gdx.Input.Keys.NUMPAD_8},
        {com.badlogic.gdx.Input.Keys.NUM_9, com.badlogic.gdx.Input.Keys.NUMPAD_9},
        {com.badlogic.gdx.Input.Keys.NUM_0, com.badlogic.gdx.Input.Keys.NUMPAD_0},
    };

    /**
     * Listener de CAPTURA no Stage: recebe a tecla antes de qualquer ator
     * (mesmo com outro ator com foco de teclado), entao nao depende da ordem
     * dos InputProcessors do WorldScreen. Ignora enquanto se digita num
     * TextField visivel (ex: chat). Cada tecla = clicar no botao.
     */
    private void registrarAtalhos(Stage stage) {
        stage.addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
            @Override
            public boolean keyDown(com.badlogic.gdx.scenes.scene2d.InputEvent event, int keycode) {
                if (!isVisible() || digitandoEmCampo(stage)) return false;
                for (int i = 0; i < ORDEM_ATALHOS.length; i++) {
                    for (int tecla : TECLAS_ATALHOS[i]) {
                        if (tecla == keycode) {
                            acionarBotao(ORDEM_ATALHOS[i]);
                            event.stop();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    private static boolean digitandoEmCampo(Stage stage) {
        com.badlogic.gdx.scenes.scene2d.Actor foco = stage.getKeyboardFocus();
        if (!(foco instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField)) return false;
        for (com.badlogic.gdx.scenes.scene2d.Actor a = foco; a != null; a = a.getParent()) {
            if (!a.isVisible()) return false;
        }
        return foco.getStage() != null;
    }

    private void acionarBotao(String nome) {
        if ("Exit".equals(nome)) {
            setVisible(false);
        } else {
            selecionarSecao(nome);
        }
    }

    public void alternar() {
        root.setVisible(!root.isVisible());
    }

    public void setVisible(boolean visible) {
        root.setVisible(visible);
    }

    public boolean isVisible() {
        return root.isVisible();
    }
}