<template>
  <div class="full">
    <!-- Portrait Layout (3-tier stack) -->
    <div v-if="showRecordViewOnBottom" class="full column portrait-container">
      <BoardPane
        class="board-section"
        :max-size="boardPaneMaxSize"
        layout-type="portrait"
        left-control-type="none"
        right-control-type="none"
        @resize="onBoardPaneResize"
      />
      <div class="controls-section" :style="{ height: `${controlPaneHeight}px` }">
        <MobileControls class="full" />
      </div>
      <div class="grow column scroll-container tab-section">
        <div class="grow tab-content-area">
          <RecordPane
            v-show="bottomUIType === BottomUIType.RECORD || bottomUIType === BottomUIType.BRANCH_TREE"
            class="full"
            :show-top-control="false"
            :show-bottom-control="false"
            :show-elapsed-time="true"
            :show-comment="true"
            :show-branch-tree="bottomUIType === BottomUIType.BRANCH_TREE"
          />
          <RecordComment
            v-show="bottomUIType === BottomUIType.COMMENT"
            class="full"
          />
          <RecordInfo
            v-show="bottomUIType === BottomUIType.INFO"
            class="full"
            :size="bottomViewSize"
          />
          <EvaluationChart
            v-show="bottomUIType === BottomUIType.CHART"
            class="full"
            :size="bottomViewSize"
            :type="EvaluationChartType.RAW"
            :thema="appSettings.thema"
            :coefficient-in-sigmoid="appSettings.coefficientInSigmoid"
          />
          <EvaluationChart
            v-show="bottomUIType === BottomUIType.PERCENTAGE_CHART"
            class="full"
            :size="bottomViewSize"
            :type="EvaluationChartType.WIN_RATE"
            :thema="appSettings.thema"
            :coefficient-in-sigmoid="appSettings.coefficientInSigmoid"
          />
          <EngineAnalytics
            v-show="bottomUIType === BottomUIType.SEARCH"
            class="full"
            :size="bottomViewSize"
            :history-mode="true"
          />
          <EngineAnalytics
            v-show="bottomUIType === BottomUIType.PV"
            class="full"
            :size="bottomViewSize"
            :history-mode="false"
          />
          <MonitorView
            v-show="bottomUIType === BottomUIType.MONITOR"
            class="full"
            :size="bottomViewSize"
          />
        </div>
        <HorizontalSelector
          v-model:value="bottomUIType"
          :items="[
            { label: t.record, value: BottomUIType.RECORD },
            { label: t.tree, value: BottomUIType.BRANCH_TREE },
            { label: t.comments, value: BottomUIType.COMMENT },
            { label: t.searchLog, value: BottomUIType.SEARCH },
            { label: t.pv, value: BottomUIType.PV },
            { label: t.evaluation, value: BottomUIType.CHART },
            { label: t.estimatedWinRate, value: BottomUIType.PERCENTAGE_CHART },
            { label: t.recordProperties, value: BottomUIType.INFO },
            { label: t.monitor, value: BottomUIType.MONITOR },
          ]"
          :height="selectorHeight"
        />
      </div>
    </div>

    <!-- Landscape Layout (Side-by-side) -->
    <div v-else class="full row landscape-container">
      <BoardPane
        class="board-section"
        :max-size="boardPaneMaxSize"
        :layout-type="boardLayoutType"
        left-control-type="none"
        right-control-type="none"
        @resize="onBoardPaneResize"
      />
      <div
        class="grow column side-section"
        :style="{ width: `${windowSize.width - boardPaneSize.width}px` }"
      >
        <div class="controls-section" :style="{ height: `${controlPaneHeight}px` }">
          <MobileControls class="full" />
        </div>
        <div class="grow tab-content-area">
          <RecordPane
            v-show="sideUIType === SideUIType.RECORD || sideUIType === SideUIType.BRANCH_TREE"
            class="full"
            :style="{
              height: `${
                sideUIType === SideUIType.BRANCH_TREE
                  ? '100%'
                  : '60%'
              }`,
            }"
            :show-top-control="false"
            :show-bottom-control="false"
            :show-elapsed-time="true"
            :show-comment="true"
            :show-branch-tree="sideUIType === SideUIType.BRANCH_TREE"
          />
          <RecordComment
            v-show="sideUIType === SideUIType.RECORD"
            :style="{
              'margin-top': '5px',
              height: '40%',
            }"
          />
          <RecordInfo v-show="sideUIType === SideUIType.INFO" class="full" :size="sideViewSize" />
          <EvaluationChart
            v-show="sideUIType === SideUIType.CHART"
            class="full"
            :size="sideViewSize"
            :type="EvaluationChartType.RAW"
            :thema="appSettings.thema"
            :coefficient-in-sigmoid="appSettings.coefficientInSigmoid"
          />
          <EvaluationChart
            v-show="sideUIType === SideUIType.PERCENTAGE_CHART"
            class="full"
            :size="sideViewSize"
            :type="EvaluationChartType.WIN_RATE"
            :thema="appSettings.thema"
            :coefficient-in-sigmoid="appSettings.coefficientInSigmoid"
          />
          <EngineAnalytics
            v-show="sideUIType === SideUIType.SEARCH"
            class="full"
            :size="sideViewSize"
            :history-mode="true"
          />
          <EngineAnalytics
            v-show="sideUIType === SideUIType.PV"
            class="full"
            :size="sideViewSize"
            :history-mode="false"
          />
          <MonitorView
            v-show="sideUIType === SideUIType.MONITOR"
            class="full"
            :size="sideViewSize"
          />
        </div>
        <HorizontalSelector
          v-model:value="sideUIType"
          :items="[
            { label: t.record, value: SideUIType.RECORD },
            { label: t.tree, value: SideUIType.BRANCH_TREE },
            { label: t.comments, value: SideUIType.COMMENT },
            { label: t.searchLog, value: SideUIType.SEARCH },
            { label: t.pv, value: SideUIType.PV },
            { label: t.evaluation, value: SideUIType.CHART },
            { label: t.estimatedWinRate, value: SideUIType.PERCENTAGE_CHART },
            { label: t.recordProperties, value: SideUIType.INFO },
            { label: t.monitor, value: SideUIType.MONITOR },
          ]"
          :height="selectorHeight"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
enum BottomUIType {
  RECORD = "record",
  BRANCH_TREE = "branchTree",
  COMMENT = "comment",
  SEARCH = "search",
  PV = "pv",
  CHART = "chart",
  PERCENTAGE_CHART = "percentageChart",
  INFO = "info",
  MONITOR = "monitor",
}
enum SideUIType {
  RECORD = "record",
  BRANCH_TREE = "branchTree",
  COMMENT = "comment",
  SEARCH = "search",
  PV = "pv",
  CHART = "chart",
  PERCENTAGE_CHART = "percentageChart",
  INFO = "info",
  MONITOR = "monitor",
}
</script>

<script setup lang="ts">
import { RectSize } from "@/common/assets/geometry";
import { BoardLayoutType } from "@/common/settings/layout";
import { Lazy } from "@/common/helpers/lazy";
import BoardPane from "@/renderer/view/main/BoardPane.vue";
import RecordPane from "@/renderer/view/main/RecordPane.vue";
import EvaluationChart from "@/renderer/view/tab/EvaluationChart.vue";
import EngineAnalytics from "@/renderer/view/tab/EngineAnalytics.vue";
import MonitorView from "@/renderer/view/monitor/MonitorView.vue";
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import MobileControls from "./MobileControls.vue";
import RecordComment from "@/renderer/view/tab/RecordComment.vue";
import HorizontalSelector from "@/renderer/view/primitive/HorizontalSelector.vue";
import { t } from "@/common/i18n";
import RecordInfo from "@/renderer/view/tab/RecordInfo.vue";
import { isIOS, isAndroid } from "@/renderer/helpers/env";
import { useAppSettings } from "@/renderer/store/settings";
import { EvaluationChartType } from "@/common/settings/layout";

const lazyUpdateDelay = 80;
const selectorHeight = 30;
const minRecordViewWidth = 180;
const minRecordViewHeight = 130;

const appSettings = useAppSettings();

// iOS の多くのバージョンでは safe-area-inset-bottom が 21px になる。
// Android はナビゲーションバーを考慮して少し余裕を持たせる。
// 横向き時は盤面最大化のため 0 にする。
const safeAreaMarginY = computed(() => {
  if (!showRecordViewOnBottom.value) {
    return 0;
  }
  return isIOS() ? 21 : isAndroid() ? 16 : 10;
});

const windowSize = reactive(
  new RectSize(window.innerWidth, window.innerHeight - (isIOS() ? 21 : isAndroid() ? 16 : 10)),
);
const bottomUIType = ref(BottomUIType.RECORD);
const sideUIType = ref(SideUIType.RECORD);

const windowLazyUpdate = new Lazy();
const updateSize = () => {
  windowLazyUpdate.after(() => {
    windowSize.width = window.innerWidth;
    windowSize.height = window.innerHeight - safeAreaMarginY.value;
  }, lazyUpdateDelay);
};

const showRecordViewOnBottom = computed(() => {
  // eslint-disable-next-line @typescript-eslint/no-unused-expressions
  windowSize.width; // dependency
  // eslint-disable-next-line @typescript-eslint/no-unused-expressions
  windowSize.height; // dependency
  return window.innerHeight > window.innerWidth;
});
const controlPaneHeight = computed(() =>
  Math.min(windowSize.height * 0.08, windowSize.width * 0.12),
);
const boardPaneMaxSize = computed(() => {
  const maxSize = new RectSize(windowSize.width, windowSize.height);
  if (showRecordViewOnBottom.value) {
    maxSize.height -= controlPaneHeight.value + minRecordViewHeight;
  } else {
    maxSize.width -= minRecordViewWidth;
  }
  return maxSize;
});
const boardLayoutType = computed(() => {
  return showRecordViewOnBottom.value ? BoardLayoutType.PORTRAIT : BoardLayoutType.COMPACT;
});

const boardPaneSize = ref(windowSize);
const onBoardPaneResize = (size: RectSize) => {
  boardPaneSize.value = size;
};

const bottomViewSize = computed(() => {
  return new RectSize(
    windowSize.width,
    windowSize.height - boardPaneSize.value.height - controlPaneHeight.value - selectorHeight,
  );
});
const sideViewSize = computed(() => {
  return new RectSize(
    windowSize.width - boardPaneSize.value.width,
    windowSize.height - controlPaneHeight.value - selectorHeight,
  );
});

onMounted(() => {
  window.addEventListener("resize", updateSize);
});

onUnmounted(() => {
  window.removeEventListener("resize", updateSize);
});
</script>

<style scoped>
.portrait-container {
  display: flex !important;
  flex-direction: column !important;
  width: 100dvw !important;
  height: 100dvh !important;
  overflow: hidden !important;
}
.landscape-container {
  display: flex !important;
  flex-direction: row !important;
  width: 100dvw !important;
  height: 100dvh !important;
  overflow: hidden !important;
}
.board-section {
  flex: 0 0 auto !important;
}
.side-section {
  height: 100% !important;
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
}
.controls-section {
  width: 100% !important;
  flex: 0 0 auto !important;
}
.tab-section {
  width: 100% !important;
  flex: 1 1 0 !important;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.tab-content-area {
  width: 100% !important;
  flex: 1 1 0 !important;
  min-height: 0;
  overflow: hidden;
}
.scroll-container {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.controls button {
  font-size: 100%;
  width: 100%;
  height: 100%;
}
.controls button .icon {
  height: 68%;
}
</style>
