# Correção de Toque Único e Soneca pelos Botões Físicos

Plano de arquitetura e implementação para assegurar a reprodução exclusiva e sem sobreposição do toque selecionado no alarme, e adicionar a funcionalidade de entrar em modo soneca imediatamente ao pressionar o botão de bloquear/desligar tela ou os botões de volume.

---

### Decisões do Usuário & Alinhamento

> [!IMPORTANT]
> Decisões confirmadas na etapa anterior de alinhamento com o usuário:

- **Comportamento dos Botões Físicos**: Tanto o botão de bloquear (Power/Screen Off) quanto os botões de volume (Volume Up/Down) ativarão a soneca do alarme.
- **Transição ao Bloquear**: O alarme será silenciado instantaneamente e reagendado para o intervalo de soneca configurado, com a tela mantida desligada para preservar a tranquilidade do usuário ao acordar.

---

### 1. Visão Geral & Conceito Central

- **O que faz**:
  1. **Eliminação de Toque Duplo**: Harmoniza os canais de notificação do Android e o motor de áudio dedicado (`AlarmSoundPlayer`). A notificação visual do sistema torna-se silenciosa para evitar que o toque nativo do canal toque em paralelo com o reprodutor customizado, garantindo que apenas o áudio selecionado (suave, digital, ringtone do sistema ou YouTube) seja executado.
  2. **Controle pelos Botões de Hardware**: Ao tocar o alarme, pressionar a tecla de bloquear/desligar a tela ou as teclas de volume aciona imediatamente o modo soneca, parando o som e reagendando o disparo sem exigir interação tátil na tela.
- **Público & Cenário**: Usuários acordando sonolentos que preferem silenciar o alarme rapidamente com gestos físicos naturais no aparelho.
- **Valor Agregado**: Fim da sobreposição cacofônica de dois toques ao mesmo tempo e facilidade imediata de acionamento de soneca às cegas.

---

### 2. Experiência do Usuário & Design Visual

- **Fluxo do Alarme com Botões de Volume**:
  1. O alarme dispara com o som e volume selecionados, acendendo a tela cheia de alarme (`AlarmRingingActivity`).
  2. O usuário pressiona Volume + ou Volume - no celular.
  3. A atividade intercepta os eventos de tecla, silencia o reprodutor de áudio e a vibração, agenda a soneca e encerra a tela exibindo feedback claro.
- **Fluxo do Alarme com Botão de Bloqueio (Power)**:
  1. O usuário aperta o botão Power para desligar a tela.
  2. O sistema emite o evento `ACTION_SCREEN_OFF`.
  3. O receptor dinâmico em `AlarmRingingActivity` detecta o bloqueio imediato, interrompe o motor de som e agenda a soneca, mantendo o telefone em repouso.
- **Design & Interface**:
  - Na tela de alarme ativo, um subtítulo informativo com ícone discreto lembrará: *"Pressione o botão de bloquear ou volume para adiar"*.
  - Feedback visual e tátil com transição suave para evitar sustos sonoros.

---

### 3. Decisões Técnicas & Trade-Offs

- **Isolamento de Áudio vs Notificação do Android**:
  - *Abordagem Selecionada*: Criar um canal de notificação dedicado silencioso (`setSound(null, null)` e sem vibração automática do sistema) com prioridade máxima (`IMPORTANCE_HIGH` / `PRIORITY_MAX`).
  - *Motivo*: No Android 8.0+, canais de notificação com som associado disparam o áudio padrão do sistema automaticamente sempre que a notificação em tela cheia é publicada. Se o app também inicia seu próprio MediaPlayer, dois sons tocam simultaneamente. Tornar o canal silencioso permite que o reprodutor de áudio do app controle 100% da reprodução com o som, volume e vibração exatos escolhidos pelo usuário.
  - *Alternativas Descartadas*: Deixar a notificação gerenciar o som impediria toques customizados do app, controle fino de volume por alarme e áudio do YouTube.

- **Detecção do Botão de Bloqueio (Power)**:
  - *Abordagem Selecionada*: Registro em tempo de execução de um `BroadcastReceiver` com `IntentFilter(Intent.ACTION_SCREEN_OFF)` durante o ciclo de vida ativo da tela de disparo.
  - *Motivo*: O Android intencionalmente bloqueia a interceptação direta de `KeyEvent.KEYCODE_POWER` por apps de terceiros por motivos de segurança do SO. Capturar `ACTION_SCREEN_OFF` e o ciclo `onPause`/`onStop` decorrente do clique do botão liga/desliga é a técnica padrão oficial e 100% confiável no ecossistema Android.

- **Detecção dos Botões de Volume**:
  - *Abordagem Selecionada*: Sobrescrita de `dispatchKeyEvent` na `AlarmRingingActivity` capturando `KEYCODE_VOLUME_UP` e `KEYCODE_VOLUME_DOWN`.
  - *Motivo*: Interceptação imediata antes que o Android altere o volume de mídia ou alarme do aparelho, transformando o clique na ação de adiar.

---

### 4. Arquitetura Técnica & Estrutura do Sistema

```
┌────────────────────────────────────────────────────────┐
│                   Disparo do Alarme                   │
│                    (AlarmReceiver)                     │
└───────────────────────────┬────────────────────────────┘
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
┌───────────────────────┐       ┌───────────────────────┐
│ Canal Notificação v2  │       │   AlarmSoundPlayer    │
│  (Som: Mudo/Silente)  │       │ (Toque Único Exato +  │
│  Prioridade Alta/UI   │       │  Vibração + Volume)   │
└───────────┬───────────┘       └───────────┬───────────┘
            │                               │
            └───────────────┬───────────────┘
                            ▼
              ┌───────────────────────────┐
              │   AlarmRingingActivity    │
              │ (Tela cheia com controles)│
              └─────────────┬─────────────┘
                            │
         ┌──────────────────┴──────────────────┐
         ▼                                     ▼
┌──────────────────┐                  ┌──────────────────┐
│  Botões Volume   │                  │  Botão Bloqueio  │
│(dispatchKeyEvent)│                  │(ACTION_SCREEN_OFF│
└────────┬─────────┘                  └────────┬─────────┘
         │                                     │
         └──────────────────┬──────────────────┘
                            ▼
               ┌────────────────────────┐
               │    Ativação Soneca     │
               │ - Stop Audio/Vibração  │
               │ - Reagenda alarme      │
               │ - Fecha Activity       │
               └────────────────────────┘
```

#### Mapeamento de Eventos e Estado

1. **Garantia de Toque Único**:
   - Atualização do canal de notificação para um identificador dedicado silencioso (`chrono_alarm_silent_channel`).
   - Sincronização centralizada no `AlarmSoundPlayer` para impedir múltiplas instâncias concorrentes de reprodução.
2. **Ciclo de Hardware na Tela de Toque**:
   - `onAttachedToWindow` / `onResume`: Registra o listener de `ACTION_SCREEN_OFF`.
   - `dispatchKeyEvent`: Captura botões de volume (`ACTION_DOWN`) e consome o evento redirecionando para a rotina de soneca.
   - `onSnoozeAlarm`: Centraliza a interrupção de mídia, o cancelamento da notificação e o agendamento da soneca no `AlarmScheduler`.
