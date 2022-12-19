export class Dataset {
  id;
  periode;
  published = false;
  publishDate;
  publishUser;
  lastUploadDate;
  lastUploadUser;
  datasets = [];
  constructor(file) {
    this.id = file.id;
    this.lastUploadDate = file.params.createdate;
    this.lastUploadUser = file.params.createuser;
    this.published = file.params.published;
    this.publishDate = file.params.publisheddate;
    this.publishUser = file.params.publisheduser;
    this.periode = this.periodeToString(
      file.params.binaryFiledataset.from,
      file.params.binaryFiledataset.to
    );
  }
  addDataset(dataset) {
    this.published = dataset.params.published ? dataset.params.published : this.published;
    this.publishDate = dataset.params.published ? dataset.params.publisheddate : this.publishDate;
    this.publishUser = dataset.params.published ? dataset.params.publisheduser : this.publishUser;
    this.lastUploadDate =
      dataset.params.createdate > this.lastUploadDate
        ? dataset.params.createdate
        : this.lastUploadDate;
    this.lastUploadUser =
      dataset.params.createdate > this.lastUploadDate
        ? dataset.params.createuser
        : this.lastUploadUser;
    this.datasets.push(dataset);
  }
  dateToString(dateString) {
    var today = new Date(dateString);
    var dd = String(today.getDate()).padStart(2, "0");
    var mm = String(today.getMonth() + 1).padStart(2, "0"); //January is 0!
    var yyyy = today.getFullYear();
    var HH = today.getHours();
    var MM = today.getMinutes();

    today = dd + "/" + mm + "/" + yyyy + " " + HH + ":" + MM;
    return today;
  }
  periodeToString(from, to) {
    if (from && to) {
      return "du " + this.dateToString(from) + " au " + this.dateToString(to);
    }
    return this.dateToString(this.params.binaryFiledataset.from, this.params.binaryFiledataset.to);
  }
  get publication() {
    if (this.published) {
      return `publié le ${this.dateToString(this.publishDate)} par ${this.publishUser.slice(0, 8)}`;
    } else {
      return "";
    }
  }
  findByUUID(uuid) {
    return this.datasets.find((e) => e.id == uuid);
  }
}
