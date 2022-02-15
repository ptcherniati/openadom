<template>
  <div>
    <canvas id="availiblity-chart" class="availiblity-chart" height="40px" :aria-label="id" />
  </div>
</template>
<script>
import { Component, Prop, Vue } from "vue-property-decorator";

@Component({})
export default class AvailiblityChart extends Vue {
  @Prop() minmax;
  @Prop() ranges;
  @Prop() id;
  @Prop({ default: true }) showDates;
  canvas = null;
  ctx = null;
  canvasW = 0;
  canvasH = 0;
  mounted() {
    this.canvas = this.$el.children[0];
    this.ctx = this.canvas.getContext("2d");
    this.canvas.width = this.width || this.$el.clientWidth;
    this.canvasW = this.canvas.width * 0.9;
    this.canvasH = this.canvas.height;
    this.draw();
  }
  writeDates() {
    if (!this.minmax?.length) return;
    let min = new Date(this.minmax[0]);
    let currentDate = new Date();
    currentDate.setTime(min.getTime());
    let max = new Date(this.minmax[1]);
    let intervale = (max.getTime() - min.getTime()) / 10;
    let formatLocalDate = (date) => date.toLocaleDateString();
    let formatDate;
    if (max.getYear() - min.getYear() > 10) {
      formatDate = (date) => date.getYear();
    } else if (max.getYear() - min.getYear() > 1) {
      formatDate = (date) =>
        date.toLocaleDateString().slice(3, 6) + date.toLocaleDateString().slice(8, 10);
    } else if (intervale / 3600 / 24 / 30 / 100 > 1) {
      formatDate = (date) => date.toLocaleDateString().slice(0, 5);
    } else {
      formatDate = (date) => date.getHours() + ":" + date.getMinutes();
    }
    this.ctx.font = "16px Arial";
    for (let i = 0; i <= 10; i++) {
      this.ctx.textAlign = "center";
      if (i == 0) this.ctx.textAlign = "start";
      if (i == 10) this.ctx.textAlign = "end";
      currentDate.setTime(min.getTime() + intervale * i);
      let inf =
        ((currentDate.getTime() - min.getTime()) / (max.getTime() - min.getTime())) * this.canvasW;
      if (this.showDates || i == 0 || i == 10) {
        this.ctx.fillText(
          i == 0 || i == 10 ? formatLocalDate(currentDate) : formatDate(currentDate),
          inf + this.canvasW * 0.05,
          (this.canvasH * 3) / 4 + 5
        );
        this.ctx.beginPath();
        this.ctx.lineWidth = 3;
        this.ctx.strokeStyle = "#9d163a";
        this.ctx.moveTo(inf + this.canvasW * 0.05, this.canvasH / 2.5);
        this.ctx.lineTo(inf + this.canvasW * 0.05, -this.canvasH);
        this.ctx.stroke();
      }
    }
  }
  drawIntervale(range) {
    var min = new Date(this.minmax[0]);
    var max = new Date(this.minmax[1]);
    let inf =
      ((new Date(range[0]).getTime() - min.getTime()) / (max.getTime() - min.getTime())) *
      this.canvasW;
    let sup =
      ((new Date(range[1]).getTime() - min.getTime()) / (max.getTime() - min.getTime())) *
      this.canvasW;

    this.ctx.beginPath();
    this.ctx.lineWidth = this.canvasH / 3;
    this.ctx.strokeStyle = "#7bee53";
    this.ctx.moveTo(inf + this.canvas.width * 0.05, this.canvasH / 4);
    this.ctx.lineTo(sup + this.canvas.width * 0.05, this.canvasH / 4);
    this.ctx.stroke();
  }
  draw() {
    for (const rangeIndex in this.ranges) {
      this.drawIntervale(this.ranges[rangeIndex]);
    }
    this.writeDates();
  }
}
</script>
<style lang="scss" scoped>
.availiblity-chart {
  margin-left: auto;
  align-items: end;
}
</style>
