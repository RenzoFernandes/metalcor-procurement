/**
 * TEMPORARY LIMITATION: the API has no endpoints for plants, cost centers, materials or units yet,
 * so these lists are a static copy of db/seed/01_master_data.sql (fictional data). Replace them
 * with API calls once lookup endpoints exist. If the seed changes, this file must change too.
 */

export interface PlantOption { id: number; code: string; name: string }
export interface CostCenterOption { id: number; code: string; name: string; plantId: number }
export interface UnitOption { id: number; code: string; description: string }
export interface MaterialOption {
  id: number
  code: string
  description: string
  unitOfMeasureId: number
  standardPrice: number
}

export const PLANTS: PlantOption[] = [
  { id: 1, code: 'SOR', name: 'Metalcor Sorocaba' },
  { id: 2, code: 'GRA', name: 'Metalcor Gravataí' },
]

export const COST_CENTERS: CostCenterOption[] = [
  { id: 1, code: 'SOR-USI', name: 'Usinagem', plantId: 1 },
  { id: 2, code: 'SOR-EST', name: 'Estamparia', plantId: 1 },
  { id: 3, code: 'SOR-PIN', name: 'Pintura', plantId: 1 },
  { id: 4, code: 'SOR-MAN', name: 'Manutenção', plantId: 1 },
  { id: 5, code: 'SOR-QUA', name: 'Qualidade', plantId: 1 },
  { id: 6, code: 'SOR-LOG', name: 'Logística', plantId: 1 },
  { id: 7, code: 'GRA-USI', name: 'Usinagem', plantId: 2 },
  { id: 8, code: 'GRA-EST', name: 'Estamparia', plantId: 2 },
  { id: 9, code: 'GRA-PIN', name: 'Pintura', plantId: 2 },
  { id: 10, code: 'GRA-MAN', name: 'Manutenção', plantId: 2 },
  { id: 11, code: 'GRA-QUA', name: 'Qualidade', plantId: 2 },
  { id: 12, code: 'GRA-LOG', name: 'Logística', plantId: 2 },
]

export const UNITS: UnitOption[] = [
  { id: 1, code: 'KG', description: 'Quilograma' },
  { id: 2, code: 'M', description: 'Metro' },
  { id: 3, code: 'UN', description: 'Unidade' },
  { id: 4, code: 'L', description: 'Litro' },
  { id: 5, code: 'PR', description: 'Par' },
]

export const MATERIALS: MaterialOption[] = [
  { id: 1, code: 'ACO-001', description: 'Bobina laminada a frio SAE 1006 1.2 mm', unitOfMeasureId: 1, standardPrice: 6.4 },
  { id: 2, code: 'ACO-002', description: 'Bobina laminada a quente 3.0 mm', unitOfMeasureId: 1, standardPrice: 5.5 },
  { id: 3, code: 'ACO-003', description: 'Chapa galvanizada 0.8 mm', unitOfMeasureId: 1, standardPrice: 7.3 },
  { id: 4, code: 'ACO-004', description: 'Barra redonda SAE 1045 Ø 40 mm', unitOfMeasureId: 1, standardPrice: 7.1 },
  { id: 5, code: 'ACO-005', description: 'Tubo de aço carbono 50 x 3 mm', unitOfMeasureId: 2, standardPrice: 38.5 },
  { id: 6, code: 'ROL-001', description: 'Rolamento rígido de esferas 6205', unitOfMeasureId: 3, standardPrice: 37.5 },
  { id: 7, code: 'ROL-002', description: 'Rolamento rígido de esferas 6305', unitOfMeasureId: 3, standardPrice: 59.5 },
  { id: 8, code: 'ROL-003', description: 'Rolamento de rolos cônicos 30206', unitOfMeasureId: 3, standardPrice: 84 },
  { id: 9, code: 'ROL-004', description: 'Rolamento de agulhas NK 25/20', unitOfMeasureId: 3, standardPrice: 67.5 },
  { id: 10, code: 'ROL-005', description: 'Mancal de pedestal UCP 205', unitOfMeasureId: 3, standardPrice: 77.5 },
  { id: 11, code: 'FER-001', description: 'Inserto de metal duro CNMG 120408', unitOfMeasureId: 3, standardPrice: 46 },
  { id: 12, code: 'FER-002', description: 'Inserto de fresamento SEKT 1204', unitOfMeasureId: 3, standardPrice: 40 },
  { id: 13, code: 'FER-003', description: 'Broca de metal duro Ø 10 mm', unitOfMeasureId: 3, standardPrice: 265 },
  { id: 14, code: 'FER-004', description: 'Fresa de topo Ø 12 mm 4 cortes', unitOfMeasureId: 3, standardPrice: 300 },
  { id: 15, code: 'FER-005', description: 'Macho de roscar M10', unitOfMeasureId: 3, standardPrice: 63.5 },
  { id: 16, code: 'TIN-001', description: 'Tinta epóxi bicomponente', unitOfMeasureId: 4, standardPrice: 54 },
  { id: 17, code: 'TIN-002', description: 'Primer rico em zinco', unitOfMeasureId: 4, standardPrice: 67.5 },
  { id: 18, code: 'TIN-003', description: 'Thinner industrial', unitOfMeasureId: 4, standardPrice: 14.5 },
  { id: 19, code: 'TIN-004', description: 'Óleo de corte solúvel', unitOfMeasureId: 4, standardPrice: 20 },
  { id: 20, code: 'TIN-005', description: 'Desengraxante industrial', unitOfMeasureId: 4, standardPrice: 11.5 },
  { id: 21, code: 'EMB-001', description: 'Caixa de papelão ondulado 40 x 30 x 25 cm', unitOfMeasureId: 3, standardPrice: 4.9 },
  { id: 22, code: 'EMB-002', description: 'Palete de madeira PBR', unitOfMeasureId: 3, standardPrice: 65 },
  { id: 23, code: 'EMB-003', description: 'Filme stretch 500 mm (rolo)', unitOfMeasureId: 3, standardPrice: 39 },
  { id: 24, code: 'EMB-004', description: 'Fita adesiva 48 mm x 100 m', unitOfMeasureId: 3, standardPrice: 6.5 },
  { id: 25, code: 'EMB-005', description: 'Saco plástico anticorrosivo VCI', unitOfMeasureId: 3, standardPrice: 1.25 },
  { id: 26, code: 'MAN-001', description: 'Contator tripolar 25 A', unitOfMeasureId: 3, standardPrice: 190 },
  { id: 27, code: 'MAN-002', description: 'Disjuntor tripolar 32 A', unitOfMeasureId: 3, standardPrice: 87.5 },
  { id: 28, code: 'MAN-003', description: 'Motor elétrico trifásico 5 cv', unitOfMeasureId: 3, standardPrice: 2600 },
  { id: 29, code: 'MAN-004', description: 'Luva de proteção nitrílica', unitOfMeasureId: 5, standardPrice: 9 },
  { id: 30, code: 'MAN-005', description: 'Óculos de proteção', unitOfMeasureId: 3, standardPrice: 9.5 },
  { id: 31, code: 'MAN-006', description: 'Graxa industrial de lítio', unitOfMeasureId: 1, standardPrice: 34.5 },
]