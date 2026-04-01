import 'package:bett_box/clash/clash.dart';
import 'package:bett_box/common/common.dart';
import 'package:bett_box/enum/enum.dart';
import 'package:bett_box/models/models.dart';
import 'package:bett_box/providers/app.dart';
import 'package:bett_box/providers/config.dart';
import 'package:bett_box/providers/state.dart';
import 'package:bett_box/state.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class ClashManager extends ConsumerStatefulWidget {
  final Widget child;

  const ClashManager({super.key, required this.child});

  @override
  ConsumerState<ClashManager> createState() => _ClashContainerState();
}

class _ClashContainerState extends ConsumerState<ClashManager>
    with AppMessageListener {
  bool _messageListenerAttached = false;

  bool get _shouldIgnoreBackgroundMessage {
    return system.isDesktop && globalState.backgroundMode.value;
  }

  void _syncMessageListener() {
    final shouldAttach = !(
      system.isDesktop && globalState.backgroundMode.value
    );
    if (shouldAttach == _messageListenerAttached) {
      return;
    }
    if (shouldAttach) {
      clashMessage.addListener(this);
    } else {
      clashMessage.removeListener(this);
    }
    _messageListenerAttached = shouldAttach;
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }

  @override
  void initState() {
    super.initState();
    globalState.backgroundMode.addListener(_syncMessageListener);
    _syncMessageListener();
    ref.listenManual(needSetupProvider, (prev, next) {
      if (prev != next) {
        globalState.appController.handleChangeProfile();
      }
    });
    ref.listenManual(coreStateProvider, (prev, next) async {
      if (prev != next) {
        await clashCore.setState(next);
      }
    });
    ref.listenManual(updateParamsProvider, (prev, next) {
      if (prev != next) {
        globalState.appController.updateClashConfigDebounce();
      }
    });

    ref.listenManual(appSettingProvider.select((state) => state.openLogs), (
      prev,
      next,
    ) {
      if (next) {
        clashCore.startLog();
      } else {
        clashCore.stopLog();
      }
    });
  }

  @override
  Future<void> dispose() async {
    globalState.backgroundMode.removeListener(_syncMessageListener);
    if (_messageListenerAttached) {
      clashMessage.removeListener(this);
      _messageListenerAttached = false;
    }
    super.dispose();
  }

  @override
  Future<void> onDelay(Delay delay) async {
    if (_shouldIgnoreBackgroundMessage) {
      return;
    }
    super.onDelay(delay);
    final appController = globalState.appController;
    appController.setDelay(delay);
    debouncer.call(FunctionTag.updateDelay, () async {
      appController.updateGroupsDebounce();
    }, duration: const Duration(milliseconds: 5000));
  }

  @override
  void onLog(Log log) {
    if (_shouldIgnoreBackgroundMessage) {
      return;
    }
    ref.read(logsProvider.notifier).addLog(log);
    if (log.logLevel == LogLevel.error) {
      globalState.showNotifier(log.payload);
    }
    super.onLog(log);
  }

  @override
  void onRequest(TrackerInfo trackerInfo) async {
    if (_shouldIgnoreBackgroundMessage) {
      return;
    }
    ref.read(requestsProvider.notifier).addRequest(trackerInfo);
    super.onRequest(trackerInfo);
  }

  @override
  Future<void> onLoaded(String providerName) async {
    if (_shouldIgnoreBackgroundMessage) {
      return;
    }
    ref
        .read(providersProvider.notifier)
        .setProvider(await clashCore.getExternalProvider(providerName));
    globalState.appController.updateGroupsDebounce();
    super.onLoaded(providerName);
  }
}
