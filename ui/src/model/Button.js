export class Button {
  id;
  label;
  iconName;
  clickCb;
  type;

  disabled = false;

  constructor(label, iconName, clickCb, type, id, disabled) {
    this.label = label;
    this.iconName = iconName;
    this.clickCb = clickCb;
    this.id = id ? id : label ? label : iconName;
    this.type = type;
    this.disabled = disabled;
  }
}
