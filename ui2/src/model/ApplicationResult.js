export class ApplicationResult {
  id;
  name;
  title;
  references = {
    idRef: {
      id: "",
      label: "",
      children: [],
      columns: {
        id: "",
        label: "",
        key: false,
        linkedTo: "",
      },
    },
  };
}
