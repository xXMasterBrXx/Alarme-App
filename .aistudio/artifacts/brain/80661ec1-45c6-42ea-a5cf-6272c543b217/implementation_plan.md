# Plano de Correção Definitiva para Alarmes no Xiaomi (MIUI / HyperOS)

## Contexto e Causa Raiz no Xiaomi
Ao fechar um aplicativo no Xiaomi (arrastando para cima na tela de recentes), a MIUI / HyperOS aplica um encerramento agressivo (`force-stop`) que suprime `BroadcastReceivers` comuns e restringe processos em segundo plano a menos que:
1. A permissão de **Início Automático (Autostart)** esteja habilitada nas configurações de segurança da Xiaomi (`com.miui.securitycenter`).
2. As permissões de **Exibir na tela de bloqueio** e **Exibir janelas pop-up em segundo plano** estejam autorizadas.
3. O canal de notificação do Android utilize som nativo do sistema com `AudioAttributes.USAGE_ALARM`, garantindo que o próprio subsistema de som do Android (SystemUI / AudioServer) toque o alarme imediatamente, sem depender exclusivamente da inicialização da JVM do aplicativo em segundo plano.
4. O `AlarmReceiver` esteja configurado com `android:directBootAware="true"` para receber broadcasts de alarme mesmo quando o armazenamento criptografado de credenciais estiver bloqueado.

---

## Proposta de Soluções

### 1. Garantia de Som Nativo no Canal de Notificação sem Som Duplo
- **Configurar Canal de Notificação com Som Nativo do Alarme**:
  - Atualizar o canal de notificação mestre (`MASTER_CHANNEL_ID`) para registrar som padrão de alarme (`RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)`) com atributos de áudio `AudioAttributes.USAGE_ALARM` e `CONTENT_TYPE_SONIFICATION`.
  - Evitar som duplo: Quando o canal de notificação do sistema emitir o som nativo de alerta, sincronizar com o `AlarmSoundPlayer` para que apenas uma fonte de áudio toque ativamente, mantendo o controle de desligar e soneca intacto.
- **Vibração Nativa no Canal**:
  - Habilitar padrão de vibração de alta intensidade diretamente no canal de notificação.

### 2. Suporte ao Direct Boot Aware e Prioridade no AndroidManifest
- Adicionar `android:directBootAware="true"` no `AlarmReceiver` para que o sistema Android possa despertá-lo em qualquer estágio de energia e tela desligada.
- Adicionar declarações de `<queries>` no `AndroidManifest.xml` para as intents da MIUI (`com.miui.securitycenter`), permitindo abrir as telas de configurações de forma confiável no Android 11+ (API 30+).

### 3. Utilitário de Diagnóstico Xiaomi / MIUI com Acesso Direto em 1 Toque
- Criar a classe utilitária `XiaomiDeviceHelper`:
  - Detectar se o dispositivo é Xiaomi / Redmi / Poco (`Build.MANUFACTURER.contains("xiaomi", ignoreCase = true)` ou detecção de MIUI/HyperOS).
  - Intent para **Início Automático**: invocar `com.miui.permcenter.autostart.AutoStartManagementActivity` ou ação equivalente da MIUI com fallback inteligente para configurações do app.
  - Intent para **Janelas Pop-up em Segundo Plano e Tela de Bloqueio**: invocar `com.miui.permcenter.permissions.PermissionsEditorActivity` direcionado diretamente ao pacote do app.
  - Intent para **Bateria sem Restrições**: atalho direto para `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Adicionar na tela de alarmes (`AlarmScreen.kt`):
  - Um card/banner destacado e moderno quando for detectado dispositivo Xiaomi, permitindo ao usuário abrir as configurações necessárias com 1 toque.
  - Diálogo explicativo passo a passo ("Configurações Essenciais Xiaomi") orientando a habilitar Início Automático e Janelas Pop-up.

---

## Arquivos a Serem Modificados / Criados
1. `/app/src/main/AndroidManifest.xml`:
   - Adicionar `android:directBootAware="true"` no `AlarmReceiver`.
   - Adicionar `<intent>` queries para `com.miui.securitycenter` e intents de autostart.
2. `/app/src/main/java/com/example/alarm/XiaomiDeviceHelper.kt` (Novo):
   - Detecção de hardware e métodos seguros para disparar as Intents específicas da MIUI / HyperOS.
3. `/app/src/main/java/com/example/alarm/AlarmReceiver.kt`:
   - Configuração de som nativo do sistema no `NotificationChannel` com atributos `USAGE_ALARM`.
   - Ajuste na coordenação de reprodução sonora entre canal do sistema e `AlarmSoundPlayer`.
4. `/app/src/main/java/com/example/alarm/AlarmSoundPlayer.kt`:
   - Garantir liberação e coordenação perfeita de áudio para evitar qualquer som duplo.
5. `/app/src/main/java/com/example/ui/alarm/AlarmScreen.kt`:
   - Adicionar o banner/card de otimização Xiaomi com botões de ação rápida para "Início Automático" e "Permissões de Segundo Plano".

---

## Verificação e Testes
- Compilar o projeto com `compile_applet` para assegurar ausência de erros de sintaxe ou dependências.
- Testar o fluxo de abertura das intents da Xiaomi e o comportamento do canal de notificação com atributos de alarme.
