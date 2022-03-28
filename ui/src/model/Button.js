export class Button {
  id;
  label;
  iconName;
  clickCb;
  type;

  constructor(label, iconName, clickCb, type, id) {
    this.label = label;
    this.iconName = iconName;
    this.clickCb = clickCb;
    this.id = id ? id : label ? label : iconName;
    this.type = type;
  }
}
