from fastapi import APIRouter

from models.schemas import MatchAssetsRequest, MatchAssetsResponse, MatchedAsset
from services.icon_service import search_iconify
from services.keyword_service import extract_keyword
from services.stock_service import find_broll

router = APIRouter()


@router.post("/match-assets", response_model=MatchAssetsResponse)
def match_assets(payload: MatchAssetsRequest) -> MatchAssetsResponse:
    assets: list[MatchedAsset] = []
    for segment in payload.segments:
        keyword, visual_type = extract_keyword(segment.text)
        asset_url = None
        source = "placeholder"
        if visual_type == "icon":
            asset_url = search_iconify(keyword)
            source = "iconify" if asset_url else "placeholder"
            if not asset_url:
                asset_url, source = find_broll(keyword)
                visual_type = "broll" if asset_url else "caption"
        else:
            asset_url, source = find_broll(keyword)
            if not asset_url:
                icon_url = search_iconify(keyword)
                if icon_url:
                    asset_url = icon_url
                    source = "iconify"
                    visual_type = "icon"
                else:
                    visual_type = "caption"
                    source = "placeholder"
        assets.append(
            MatchedAsset(
                segmentId=segment.segmentId,
                keyword=keyword,
                visualType=visual_type,
                assetSource=source,
                assetUrl=asset_url,
            )
        )
    return MatchAssetsResponse(assets=assets)
