# Notas do projeto

## Versao client/servidor
- A cada mudanca grande, subir a versao nos DOIS lugares, com o mesmo valor:
  `servidor.py::SERVER_VERSION` e `rede/ServerConfig.java::CLIENT_VERSION`
  (ex: v0.2 -> v0.3).
- O client manda a versao em `/check_version` ao apertar Play; se nao bater, o
  servidor responde 426 e o client mostra "Version outdated, please update your game".

## Balanceamento de mobs (tempo pra matar)
- Mob da hunt deve morrer, SO' com ataque basico (2.385s por golpe), em:
  hunt ate' lv 20 -> ~15s | lv 40 -> ~20s | lv 60 -> ~25s | lv 80 -> ~30s (+5s a cada 20 levels).
- Abaixo do tempo = lucro (XP e gold), acima = prejuizo. As magias (a do lv 7, ~70% a cada 5s;
  AOE do lv 20) sao a margem de lucro: com elas o mob morre ~25% mais rapido.
- Conta: HP do mob ~= golpes x dano medio por golpe do player da fase
  (level x0.5 + arma + offhand ADC + skill da classe).
- Referencias calibradas: Wolf 300 HP (lv ~12, gear lv 10), Black Spider 450 HP (lv ~22, gear lv 20; com o gear lv20 novo morre um pouco mais rapido - mantido assim de proposito).
- Gear novo (main hand + offhand) deve dar ~+15 de dano por tier em relacao ao anterior
  (ex: Mage lv10 staff 8 + book 9 = 17 -> lv20 staff 18 + book 14 = 32), pra troca ser sentida.
- Economia: profit do player deve ir 70-80% pro refill (pots, comida, flechas, runas). NPC paga
  pouco por drop de equipamento (sell_price fixo); o valor real do item e' no mercado.
