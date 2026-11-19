create table if not exists prison
(
    code varchar(6) not null,
    name text       not null,
    version int not null default 0,
    constraint pk_prison primary key (code)
)
;

-- initial load to allow adding of the FK constraint
insert into prison (code, name)
values ('AKI', 'Acklington (HMP)'),
       ('ALI', 'Albany (HMP)'),
       ('ANI', 'Aldington (HMP)'),
       ('ACI', 'Altcourse (HMP & YOI)'),
       ('ASI', 'Ashfield (HMP)'),
       ('AWI', 'Ashwell (HMP)'),
       ('AGI', 'Askham Grange (HMP & YOI)'),
       ('AYI', 'Aylesbury (HMP)'),
       ('BFI', 'Bedford (HMP)'),
       ('BAI', 'Belmarsh (HMP)'),
       ('BWI', 'Berwyn (HMP)'),
       ('BMI', 'Birmingham (HMP)'),
       ('BTI', 'Blakenhurst (HMP)'),
       ('BHI', 'Blantyre House (HMP)'),
       ('BDI', 'Blundeston (HMP)'),
       ('BSI', 'Brinsford (HMP & YOI)'),
       ('BLI', 'Bristol (HMP)'),
       ('BXI', 'Brixton (HMP)'),
       ('BKI', 'Brockhill (HMP & YOI)'),
       ('BZI', 'Bronzefield (HMP & YOI)'),
       ('BCI', 'Buckley Hall (HMP)'),
       ('BNI', 'Bullingdon (HMP)'),
       ('BUI', 'Bullwood Hall (HMP)'),
       ('BRI', 'Bure (HMP)'),
       ('CHI', 'Camp Hill (HMP)'),
       ('CPI', 'Campsfield House (HMYOI)'),
       ('CYI', 'Canterbury (HMP)'),
       ('CFI', 'Cardiff (HMP)'),
       ('CSI', 'Castington (HMP & YOI)'),
       ('CWI', 'Channings Wood (HMP)'),
       ('CDI', 'Chelmsford (HMP & YOI)'),
       ('CEI', 'Cleland (HMP)'),
       ('CRI', 'Colchester (hmYOI)'),
       ('CLI', 'Coldingley (HMP)'),
       ('CKI', 'Cookham Wood (HMP)'),
       ('DAI', 'Dartmoor (HMP)'),
       ('DTI', 'Deerbolt (HMP & YOI)'),
       ('DNI', 'Doncaster (HMP)'),
       ('DRI', 'Dorchester (HMP)'),
       ('DGI', 'Dovegate (HMP)'),
       ('DVI', 'Dover Immigration Removal Centre'),
       ('DWI', 'Downview (HMP & YOI)'),
       ('DHI', 'Drake Hall (HMP & YOI)'),
       ('DMI', 'Durham (HMP)'),
       ('ESI', 'East Sutton Park (HMP & YOI)'),
       ('EWI', 'Eastwood Park (HMP & YOI)'),
       ('NEI', 'Edmunds Hill (HMP)'),
       ('EYI', 'Elmley (HMP)'),
       ('EEI', 'Erlestoke (HMP)'),
       ('EVI', 'Everthorpe (HMP)'),
       ('EXI', 'Exeter (HMP)'),
       ('FSI', 'Featherstone (HMP)'),
       ('FYI', 'Feltham A (HMYOI)'),
       ('FMI', 'Feltham B (HMP & YOI)'),
       ('FII', 'Finnamore (hmYOI)'),
       ('FWI', 'Five Wells (HMP)'),
       ('FDI', 'Ford (HMP)'),
       ('FOI', 'Foreign Prison'),
       ('FBI', 'Forest Bank (HMP)'),
       ('FEI', 'Fosse Way (HMP)'),
       ('FHI', 'Foston Hall (HMP & YOI)'),
       ('FKI', 'Frankland (HMP)'),
       ('FNI', 'Full Sutton (HMP)'),
       ('GHI', 'Garth (HMP)'),
       ('GTI', 'Gartree (HMP)'),
       ('ZZGHI', 'Ghost Holding Establishment'),
       ('GPI', 'Glen Parva (HMPYOI & Rc)'),
       ('GLI', 'Gloucester (HMP)'),
       ('GNI', 'Grendon (HMP)'),
       ('GMI', 'Guys Marsh (HMP)'),
       ('HRI', 'Haslar Immigration Removal Centre'),
       ('HDI', 'Hatfield (HMP & YOI)'),
       ('HVI', 'Haverigg (HMP)'),
       ('HGI', 'Hewell Grange (HMP)'),
       ('HEI', 'Hewell (HMP)'),
       ('HOI', 'High Down (HMP & YOI)'),
       ('HPI', 'Highpoint (HMP)'),
       ('HII', 'Hindley (HMP & YOI)'),
       ('HBI', 'Hollesley Bay (HMP & YOI)'),
       ('HYI', 'Holloway (HMP)'),
       ('HHI', 'Holme House (HMP)'),
       ('HLI', 'Hull (HMP)'),
       ('HMI', 'Humber (HMP)'),
       ('HCI', 'Huntercombe (HMP)'),
       ('ISI', 'Isis (HMP & YOI)'),
       ('IWI', 'Isle of Wight (HMP)'),
       ('KTI', 'Kennet (HMP)'),
       ('PTI', 'Kingston (HMP)'),
       ('KMI', 'Kirkham (HMP)'),
       ('KVI', 'Kirklevington Grange (HMP)'),
       ('LAI', 'Lancaster Castle (HMP)'),
       ('LFI', 'Lancaster Farms (HMP)'),
       ('LMI', 'Latchmere House (HMP)'),
       ('LEI', 'Leeds (HMP)'),
       ('LCI', 'Leicester (HMP)'),
       ('LWI', 'Lewes (HMP)'),
       ('LYI', 'Leyhill (HMP)'),
       ('LII', 'Lincoln (HMP & YOI)'),
       ('LHI', 'Lindholme (HMP)'),
       ('LTI', 'Littlehey (HMP)'),
       ('LPI', 'Liverpool (HMP)'),
       ('LLI', 'Long Lartin (HMP)'),
       ('LGI', 'Lowdham Grange (HMP)'),
       ('LNI', 'Low Newton (HMP & YOI)'),
       ('MSI', 'Maidstone (HMP)'),
       ('MRI', 'Manchester (HMP)'),
       ('MWI', 'Medway (STC)'),
       ('MKI', 'Millsike (HMP)'),
       ('MDI', 'Moorland (HMP & YOI)'),
       ('MHI', 'Morton Hall (HMP)'),
       ('NHI', 'New Hall (HMP & YOI)'),
       ('NNI', 'Northallerton (HMP)'),
       ('NOI', 'Northeye (HMP)'),
       ('NSI', 'North Sea Camp (HMP)'),
       ('NLI', 'Northumberland (HMP)'),
       ('NWI', 'Norwich (HMP & YOI)'),
       ('NMI', 'Nottingham (HMP)'),
       ('OWI', 'Oakwood (HMP)'),
       ('ONI', 'Onley (HMP)'),
       ('OXI', 'Oxford (HMP)'),
       ('PYI', 'Parc A (HMYOI)'),
       ('PRI', 'Parc (HMP)'),
       ('PKI', 'Parkhurst (HMP)'),
       ('PVI', 'Pentonville (HMP & YOI)'),
       ('PFI', 'Peterborough Female (HMP & YOI)'),
       ('PBI', 'Peterborough (HMP & YOI)'),
       ('PDI', 'Portland (HMP & YOI)'),
       ('UPI', 'Prescoed (HMP & YOI)'),
       ('PNI', 'Preston (HMP)'),
       ('PCI', 'Pucklechurch (hmrc)'),
       ('RNI', 'Ranby (HMP)'),
       ('RDI', 'Reading (HMP & YOI)'),
       ('RSI', 'Risley (HMP)'),
       ('RCI', 'Rochester (HMP & YOI)'),
       ('RLI', 'Rollestone (HMP)'),
       ('RUI', 'Rudgate (HMP)'),
       ('RHI', 'Rye Hill (HMP)'),
       ('SDI', 'Send (HMP)'),
       ('SMI', 'Shepton Mallet (HMP)'),
       ('SYI', 'Shrewsbury (HMP)'),
       ('SPI', 'Spring Hill (HMP)'),
       ('SFI', 'Stafford (HMP)'),
       ('EHI', 'Standford Hill (HMP)'),
       ('SKI', 'Stocken (HMP)'),
       ('SHI', 'Stoke Heath (HMP & YOI)'),
       ('STI', 'Styal (HMP & YOI)'),
       ('SUI', 'Sudbury (HMP)'),
       ('SLI', 'Swaleside (HMP)'),
       ('SWI', 'Swansea (HMP)'),
       ('SNI', 'Swinfen Hall (HMP & YOI)'),
       ('TSI', 'Thameside (HMP)'),
       ('MTI', 'The Mount (HMP)'),
       ('VEI', 'The Verne (HMP)'),
       ('WAI', 'The Weare (HMP)'),
       ('TCI', 'Thorn Cross (HMP & YOI)'),
       ('TAI', 'Thorp Arch (HMP)'),
       ('UKI', 'Usk (HMP)'),
       ('WDI', 'Wakefield (HMP)'),
       ('WWI', 'Wandsworth (HMP)'),
       ('WII', 'Warren Hill (HMP)'),
       ('WLI', 'Wayland (HMP)'),
       ('WEI', 'Wealstun (HMP)'),
       ('WBI', 'Wellingborough (HMP)'),
       ('WNI', 'Werrington (HMYOI)'),
       ('WYI', 'Wetherby (HMYOI)'),
       ('WTI', 'Whatton (HMP)'),
       ('WRI', 'Whitemoor (HMP)'),
       ('WCI', 'Winchester (HMP & YOI)'),
       ('WOI', 'Wolds (HMP)'),
       ('WHI', 'Woodhill (HMP & YOI)'),
       ('WSI', 'Wormwood Scrubs (HMP)'),
       ('WMI', 'Wymott (HMP)')
on conflict do nothing
;

do
$$
    begin
        if not exists (select 1
                       from pg_constraint con
                                join pg_class rel on rel.oid = con.conrelid
                                join pg_namespace nsp on nsp.oid = con.connamespace
                       where nsp.nspname = 'public'
                         and rel.relname = 'allocation'
                         and con.conname = 'fk_allocation_prison_code') then
            alter table allocation
                add constraint fk_allocation_prison_code foreign key (prison_code) references prison (code)
            ;
        end if;
        if not exists (select 1
                       from pg_constraint con
                                join pg_class rel on rel.oid = con.conrelid
                                join pg_namespace nsp on nsp.oid = con.connamespace
                       where nsp.nspname = 'public'
                         and rel.relname = 'prison_statistic'
                         and con.conname = 'fk_prison_statistic_prison_code') then
            alter table prison_statistic
                add constraint fk_prison_statistic_prison_code foreign key (prison_code) references prison (code)
            ;
        end if;
        if not exists (select 1
                       from pg_constraint con
                                join pg_class rel on rel.oid = con.conrelid
                                join pg_namespace nsp on nsp.oid = con.connamespace
                       where nsp.nspname = 'public'
                         and rel.relname = 'staff_role'
                         and con.conname = 'fk_staff_role_prison_code') then
            alter table staff_role
                add constraint fk_staff_role_prison_code foreign key (prison_code) references prison (code)
            ;
        end if;
    end
$$;