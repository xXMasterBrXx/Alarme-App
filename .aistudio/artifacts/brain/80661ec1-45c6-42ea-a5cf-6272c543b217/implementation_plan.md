# Execução Confiável do Alarme com Aplicativo Fechado

Plano técnico para garantir que o despertador dispare de forma pontual e confiável mesmo quando o aplicativo estiver totalmente fechado, finalizado na gaveta de recentes ou com a tela desligada.

---

### Decisões do Usuário & Alinhamento

> [!IMPORTANT]
> Decisões alinhadas na etapa de perguntas:

- **Estratégia de Execução em Segundo Plano**: Utilização de **Serviço em Primeiro Plano (Foreground Service)** acionado automaticamente no instante exato do disparo, garantindo que o Android mantenha o processo ativo com WakeLock até que o usuário decida desligar ou adiar.
- **Experiência do Usuário**: Foco 100% em ajustes técnicos transparentes no código e manifesto (sem necessidade de tutoriais ou telas adicionais de configuração manual).

---

### 1. Visão Geral & Causa Raiz

- **Diagnóstico Técnico do Problema Atual**:
  1. *Ciclo Efêmero do BroadcastReceiver*: Quando o aplicativo é fechado, o sistema operacional encerra seu processo. Ao chegar a hora do alarme, o `AlarmManager` entrega o evento ao `AlarmReceiver`. Contudo, assim que o método `onReceive()` termina (em cerca de 5 a 10 milissegundos), o Android classifica o processo como vazio (*cached/empty process*) e o finaliza sumariamente antes que a reprodução sonora ou a atividade em tela cheia se consolidem.
  2. *Ausência de Foreground Service Ativo*: O `AlarmService` não estava sendo iniciado como serviço de primeiro plano (`startForegroundService` com `startForeground`), deixando o reprodutor de áudio desprotegido contra o gerenciador de memória do sistema.
  3. *Compatibilidade com Android 14+*: Serviços em primeiro plano exigem declaração explícita de `android:foregroundServiceType="mediaPlayback"` no `AndroidManifest.xml` e na chamada de `startForeground()`.
- **Solução Entregue**:
  - Transformação do `AlarmService` em um Foreground Service de alta prioridade (`mediaPlayback`), imune ao encerramento pelo sistema operacional.
  - O `AlarmReceiver` delega a execução contínua ao `AlarmService` mantendo WakeLock compartilhado.
  - A notificação em tela cheia (`fullScreenIntent`) é atrelada diretamente ao ciclo do serviço, garantindo abertura visual e áudio ininterrupto.

---

### 2. Experiência do Usuário & Fluxo de Disparo

```
App Fechado (Processo Inativo) ──► AlarmManager dispara no minuto exato
                                            │
                                            ▼
                                   AlarmReceiver (WakeLock)
                                            │
                                            ▼
                               ContextCompat.startForegroundService
                                            │
                                            ▼
                                  AlarmService.startForeground
                               (Notificação Heads-up / Tela Cheia)
                                            │
                   ┌────────────────────────┴────────────────────────┐
                   ▼                                                 ▼
          Áudio & Vibração Ativos                          AlarmRingingActivity
         (Processo Blindado pelo SO)                       (Acende Tela Bloqueada)
```

1. **Repouso**: O app pode estar completamente fechado; o alarme permanece registrado no hardware via `AlarmManager.setAlarmClock()`.
2. **Disparo**:
   - O `AlarmReceiver` recebe o sinal do sistema com permissão de despertar (`WAKE_LOCK`).
   - O `AlarmService` é elevado a Foreground Service imediatamente, exibindo a notificação prioritária e tocando o áudio via `AlarmSoundPlayer`.
   - A atividade em tela cheia (`AlarmRingingActivity`) assume a tela mesmo que o celular esteja bloqueado.
3. **Encerramento / Soneca**: Ao desligar ou apertar o botão de bloqueio/volume para adiar, o serviço é finalizado e libera os recursos da CPU.

---

### 3. Decisões Técnicas & Trade-Offs

- **Foreground Service sob Demanda vs Serviço Persistente Contínuo**:
  - *Abordagem Selecionada*: Ativação do Foreground Service estritamente no momento do disparo do alarme.
  - *Vantagem*: Zero consumo de bateria enquanto o alarme aguarda o horário, sem poluir a barra de status com notificações permanentes desnecessárias.
  - *Conformidade*: O Android 12, 13 e 14 autorizam expressamente a inicialização de Foreground Service a partir do plano de fundo quando disparado por `setAlarmClock()`.

- **Declaração de Tipo de Serviço no AndroidManifest**:
  - Inclusão de `android:foregroundServiceType="mediaPlayback"` na tag `<service android:name=".alarm.AlarmService" />`.
  - Atende aos requisitos rigorosos do Android 14 e Google Play sem risco de exceções em tempo de execução.

- **Gerenciamento de WakeLock e goAsync**:
  - Utilização de `WakeLock` estático seguro com limite de tempo e liberação estrita pelo `AlarmService` ao finalizar, prevenindo suspensão da CPU por fabricantes agressivos (como Xiaomi, Samsung e Motorola).

---

### 4. Arquitetura Técnica & Componentes

```
┌────────────────────────────────────────────────────────┐
│                   AlarmScheduler                       │
│    setAlarmClock(triggerTime, showPendingIntent)       │
└───────────────────────────┬────────────────────────────┘
                            │ (Na hora exata)
                            ▼
┌────────────────────────────────────────────────────────┐
│                    AlarmReceiver                       │
│ - Segura WakeLock estático                             │
│ - ContextCompat.startForegroundService(AlarmService)   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                    AlarmService                        │
│ - startForeground(id, notification, mediaPlayback)     │
│ - Inicia AlarmSoundPlayer.play(...)                    │
│ - Lança AlarmRingingActivity (fullScreenIntent)        │
│ - Mantém processo vivo até Desligar/Soneca             │
└───────────────────────────┬────────────────────────────┘
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
┌───────────────────────┐       ┌───────────────────────┐
│     Desligar          │       │      Soneca           │
│ - AlarmService.stop() │       │ - AlarmService.stop() │
│ - AlarmSoundPlayer    │       │ - Reagenda alarme     │
└───────────────────────┘       └───────────────────────┘
```

#### Mapeamento de Arquivos e Funções:
- `AndroidManifest.xml`: Atualização da declaração do `AlarmService` com `foregroundServiceType="mediaPlayback"`.
- `AlarmService.kt`: Implementação completa de `startForeground`, suporte a Android 14 (`mediaPlayback`), controle de áudio centralizado e parada segura.
- `AlarmReceiver.kt`: Acionamento imediato do Foreground Service ao receber `ACTION_ALARM_TRIGGER`, garantindo imunidade a encerramento de processo.
- `AlarmRingingActivity.kt`: Coordenação com o `AlarmService` para que ações de soneca e cancelamento liberem o serviço uniformemente.
